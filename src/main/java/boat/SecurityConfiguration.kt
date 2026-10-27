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
import java.security.MessageDigest
import java.util.Base64

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
            .headers { headers -> headers.contentSecurityPolicy { it.policyDirectives(CONTENT_SECURITY_POLICY) } }

        return http.build()
    }

    companion object {
        private fun sha256Source(script: String): String =
            "'sha256-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.toByteArray(Charsets.UTF_8))) + "'"

        /**
         * The pages are html of their own with names of search sites and hosters in them. They are escaped, should
         * something slip through anyway it can't run a script: the only script is the confirmation of the delete
         * button (by its hash), styles are inline, forms only go to boat and no other site can show boat in a frame.
         */
        internal val CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; " +
                "script-src 'unsafe-hashes' ${sha256Source(BoatController.CONFIRM_DELETE_SCRIPT)}; " +
                "form-action 'self'; frame-ancestors 'none'; base-uri 'none'"
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