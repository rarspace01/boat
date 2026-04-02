package boat

import boat.utilities.LoggerDelegate
import org.apache.commons.io.FileUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    companion object {
        private val log by LoggerDelegate()
    }

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain? {
        val htpasswdFile = File("htpasswd")
        val htpasswdExists = htpasswdFile.exists()

        http
            .authorizeHttpRequests { authorize ->
                if (htpasswdExists) {
                    authorize
                        .requestMatchers("/boat/shutdown", "/").permitAll()
                        .anyRequest().authenticated()
                } else {
                    authorize.anyRequest().permitAll()
                }
            }
            .csrf { csrf -> csrf.disable() }

        if (htpasswdExists) {
            http
                .httpBasic(Customizer.withDefaults<HttpBasicConfigurer<HttpSecurity>>())
                .formLogin(Customizer.withDefaults<FormLoginConfigurer<HttpSecurity>>())
        }

        return http.build()
    }

    @Bean
    fun userDetailsService(): UserDetailsService {
        val users: MutableList<UserDetails> = ArrayList()
        val htpasswdFile = File("htpasswd")
        if (htpasswdFile.exists()) {
            try {
                val lines = FileUtils.readLines(htpasswdFile, StandardCharsets.UTF_8)
                for (line in lines) {
                    if (line.contains(":") && !line.startsWith("#")) {
                        val parts = line.split(":".toRegex(), limit = 2).toTypedArray()
                        val username = parts[0]
                        var password = parts[1]

                        // Handle Apache htpasswd format for BCrypt ($2y$)
                        // Spring Security expects {id}encodedPassword format or just BCrypt if it's the default.
                        // Since htpasswd often uses $2y$ (BCrypt variation), we need to ensure it's compatible.
                        // $2y$ is compatible with $2a$ which Spring's BCrypt uses.
                        if (password.startsWith("$2y$")) {
                            password = "{bcrypt}" + password.replaceFirst("\\$2y\\$".toRegex(), "\\$2a\\$")
                        } else if (password.startsWith("\$apr1$")) {
                            // Apache MD5-based crypt is not natively supported by Spring Security's standard encoders
                            // we skip it or log a warning
                            log.warn(
                                "MD5-based Apache crypt (\$apr1$) is not supported for user: {}",
                                username
                            )
                            continue
                        } else {
                            // Assume plain text or other if no prefix, but htpasswd is usually hashed.
                            // If it's not starting with a known prefix, we might need a custom matcher.
                            log.warn("Unknown password format for user: {}", username)
                        }

                        users.add(
                            User.withUsername(username)
                                .password(password)
                                .roles("USER")
                                .build()
                        )
                    }
                }
            } catch (e: IOException) {
                log.error("Could not read htpasswd file", e)
            }
        } else {
            log.error("htpasswd file not found at: {}", htpasswdFile.getAbsolutePath())
        }

        return InMemoryUserDetailsManager(users)
    }
}
