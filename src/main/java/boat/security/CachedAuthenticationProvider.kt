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

    // Cache: Key = keyed hash of "username:password", Value = Boolean (isValid)
    // The plaintext credentials are never kept. Expires entries 10 minutes after write, so a removed user or a changed
    // password is still accepted for up to 10 minutes, in exchange for no database lookup and no BCrypt check per request.
    private val authCache: Cache<String, Boolean> = Caffeine.newBuilder()
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
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))

        // 1. Instant Cache Hit
        if (authCache.getIfPresent(cacheKey) == true) {
            return UsernamePasswordAuthenticationToken(username, password, authorities)
        }

        // 2. Database/BCrypt Fallback
        val user = userRepository.findByUsername(username)
        if (user != null && passwordEncoder.matches(password, user.password)) {
            authCache.put(cacheKey, true)
            return UsernamePasswordAuthenticationToken(username, password, authorities)
        }

        throw BadCredentialsException("Invalid credentials")
    }

    override fun supports(authentication: Class<*>): Boolean =
        authentication == UsernamePasswordAuthenticationToken::class.java
}