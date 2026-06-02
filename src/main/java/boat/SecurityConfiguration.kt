package boat

import boat.repositories.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.firewall.HttpFirewall
import org.springframework.security.web.firewall.StrictHttpFirewall

@Configuration
@EnableWebSecurity
class SecurityConfiguration(private val userRepository: UserRepository) {

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val hasUsers = userRepository.count() > 0

        http
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .csrf { csrf ->
                csrf.disable()
            }
            .authorizeHttpRequests { authorize ->
                if (hasUsers) {
                    authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/PFDB", "/PFDB/", "/PFDB/**").permitAll()
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
                "GET",
                "HEAD",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS",
                "PATCH",
                "PROPFIND",
                "MKCOL",
                "MOVE",
                "COPY",
                "LOCK",
                "UNLOCK",
                "PROPPATCH"
            )
        )
        return firewall
    }
}
