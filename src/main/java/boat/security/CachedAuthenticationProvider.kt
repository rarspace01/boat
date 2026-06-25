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
import java.util.concurrent.TimeUnit

@Component
class CachedAuthenticationProvider(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : AuthenticationProvider {

    // Cache: Key = "username:password", Value = Boolean (isValid)
    // Expires entries 10 minutes after write to ensure security/freshness
    private val authCache: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    override fun authenticate(authentication: Authentication): Authentication {
        val username = authentication.name
        val password = authentication.credentials.toString()
        val cacheKey = "$username:$password"

        // 1. Instant Cache Hit
        if (authCache.getIfPresent(cacheKey) == true) {
            return UsernamePasswordAuthenticationToken(username, password, listOf(SimpleGrantedAuthority("ROLE_USER")))
        }

        // 2. Database/BCrypt Fallback
        val user = userRepository.findByUsername(username)
        if (user != null && passwordEncoder.matches(password, user.password)) {
            authCache.put(cacheKey, true)
            return UsernamePasswordAuthenticationToken(username, password, listOf(SimpleGrantedAuthority("ROLE_USER")))
        }

        throw BadCredentialsException("Invalid credentials")
    }

    override fun supports(authentication: Class<*>): Boolean =
        authentication == UsernamePasswordAuthenticationToken::class.java
}