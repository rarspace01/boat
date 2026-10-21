package boat.security

import boat.repositories.UserRepository
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class CachedAuthenticationProvider(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : AuthenticationProvider {

    // Cache: Key = keyed hash of "username:password", Value = the stored password hash the credentials were verified against.
    // The plaintext credentials are never kept, and a hit is only valid while the user still has that password hash,
    // so revoked users and changed passwords are rejected right away. The cache only saves the expensive BCrypt check.
    private val authCache: Cache<String, String> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    // random per start, so the cached keys are useless outside of this process
    private val cacheKeySecret = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private fun cacheKey(username: String, password: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(cacheKeySecret, "HmacSHA256")) }
        // the length prefix keeps ("ab", "c") and ("a", "bc") apart
        mac.update("${username.length}:$username:$password".toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(mac.doFinal())
    }

    override fun authenticate(authentication: Authentication): Authentication {
        val username = authentication.name
        val password = authentication.credentials.toString()
        val cacheKey = cacheKey(username, password)
        val user = userRepository.findByUsername(username)
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))

        // 1. Instant Cache Hit, as long as the stored password is still the verified one
        if (user != null && authCache.getIfPresent(cacheKey) == user.password) {
            return UsernamePasswordAuthenticationToken(username, password, authorities)
        }

        // 2. BCrypt Fallback
        if (user != null && passwordEncoder.matches(password, user.password)) {
            authCache.put(cacheKey, user.password)
            return UsernamePasswordAuthenticationToken(username, password, authorities)
        }

        throw BadCredentialsException("Invalid credentials")
    }

    override fun supports(authentication: Class<*>): Boolean =
        authentication == UsernamePasswordAuthenticationToken::class.java
}