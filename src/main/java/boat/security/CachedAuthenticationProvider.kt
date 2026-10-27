package boat.security

import boat.repositories.UserRepository
import boat.utilities.LoggerDelegate
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.WebAuthenticationDetails
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

    // Failed logins per client address, counted from the first one for FAILED_LOGIN_WINDOW_MINUTES. Above
    // MAX_FAILED_LOGINS the password isn't checked any more: no endless guessing, and no BCrypt check (the expensive
    // part) per request of someone guessing. Logins in the cache still work.
    private val failedLoginsByAddress: Cache<String, AtomicInteger> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(FAILED_LOGIN_WINDOW_MINUTES, TimeUnit.MINUTES)
        .build()

    // How long the last BCrypt check of an existing user took. A user who doesn't exist waits that long instead of
    // running BCrypt (no CPU load for nobody), so the time of the answer doesn't tell which users exist.
    @Volatile
    private var bcryptCheckMillis = DEFAULT_BCRYPT_CHECK_MILLIS

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

        val address = (authentication.details as? WebAuthenticationDetails)?.remoteAddress ?: "unknown"
        if ((failedLoginsByAddress.getIfPresent(address)?.get() ?: 0) >= MAX_FAILED_LOGINS) {
            throw BadCredentialsException("Too many failed logins")
        }

        // 2. Database/BCrypt Fallback
        val user = userRepository.findByUsername(username)
        if (user == null) {
            waitLikeABcryptCheck()
        } else {
            val started = System.nanoTime()
            val passwordMatches = passwordEncoder.matches(password, user.password)
            bcryptCheckMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            if (passwordMatches) {
                authCache.put(cacheKey, true)
                return UsernamePasswordAuthenticationToken(username, password, authorities)
            }
        }

        val failedLogins = failedLoginsByAddress.get(address) { AtomicInteger() }.incrementAndGet()
        if (failedLogins == MAX_FAILED_LOGINS) {
            logger.warn("{} failed logins from [{}], no more logins are checked from there for up to {} minutes",
                failedLogins, address, FAILED_LOGIN_WINDOW_MINUTES)
        }
        throw BadCredentialsException("Invalid credentials")
    }

    private fun waitLikeABcryptCheck() {
        try {
            Thread.sleep(bcryptCheckMillis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun supports(authentication: Class<*>): Boolean =
        authentication == UsernamePasswordAuthenticationToken::class.java

    companion object {
        private val logger by LoggerDelegate()
        const val MAX_FAILED_LOGINS = 20
        const val FAILED_LOGIN_WINDOW_MINUTES = 10L
        private const val DEFAULT_BCRYPT_CHECK_MILLIS = 100L
    }
}