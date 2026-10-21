package boat

import boat.security.CachedAuthenticationProvider
import boat.security.CrossSiteRequestFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.firewall.HttpFirewall
import org.springframework.security.web.firewall.StrictHttpFirewall

@Configuration
@EnableWebSecurity
class SecurityConfiguration(private val cachedAuthenticationProvider: CachedAuthenticationProvider) {

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity, authManager: AuthenticationManager): SecurityFilterChain {
        http
            .cors { it.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            // no tokens, see CrossSiteRequestFilter
            .csrf { csrf -> csrf.disable() }
            .addFilterBefore(CrossSiteRequestFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .authenticationManager(authManager)
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(HttpMethod.OPTIONS).permitAll()
                    .requestMatchers("/boat/shutdown", "/").permitAll()
                    .anyRequest().authenticated()
            }
            .httpBasic(Customizer.withDefaults())

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

    @Bean
    fun authManager(http: HttpSecurity): AuthenticationManager {
        val builder = http.getSharedObject(AuthenticationManagerBuilder::class.java)
        builder.authenticationProvider(cachedAuthenticationProvider)
        return builder.build()
    }
}