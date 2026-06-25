package boat

import boat.repositories.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.firewall.HttpFirewall
import org.springframework.security.web.firewall.StrictHttpFirewall
import java.util.Collections

@Configuration
@EnableWebSecurity
class SecurityConfiguration(private val userRepository: UserRepository) {

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val hasUsers = userRepository.count() > 0

        http
            .cors { it.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .csrf { csrf ->
                csrf.disable()
            }
            .authorizeHttpRequests { authorize ->
                if (hasUsers) {
                    authorize
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers("/boat/shutdown", "/").permitAll()
                        .anyRequest().authenticated()
                } else {
                    authorize.anyRequest().permitAll()
                }
            }

        if (hasUsers) {
            http.httpBasic(Customizer.withDefaults())
        }

        return http.build()
    }

    @Bean
    fun httpFirewall(): HttpFirewall {
        val firewall = StrictHttpFirewall()
        firewall.setAllowedHttpMethods(
            listOf(
                "GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS",
                "PATCH", "PROPFIND", "MKCOL", "MOVE", "COPY",
                "LOCK", "UNLOCK", "PROPPATCH"
            )
        )
        return firewall
    }

    /**
     * Caches password verification to prevent CPU exhaustion and connection
     * drops when Kodi sends 100+ concurrent stateless Basic Auth requests.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return object : PasswordEncoder {
            private val delegate = BCryptPasswordEncoder()

            // A thread-safe, bounded cache (max 1000 entries) to prevent OOM memory leaks
            private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean {
                    return size > 1000
                }
            })

            override fun encode(rawPassword: CharSequence?): String? {
                return delegate.encode(rawPassword)
            }

            override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
                val key = "$rawPassword::$encodedPassword"
                // Only run the heavy BCrypt math if we haven't seen this exact combo recently
                return cache.computeIfAbsent(key) {
                    delegate.matches(rawPassword, encodedPassword)
                }
            }
        }
    }
}