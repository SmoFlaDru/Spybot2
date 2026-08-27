package com.spybot.web.config

import com.spybot.core.service.AuthenticationService
import com.spybot.web.filter.LastSeenFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern
import org.springframework.security.web.util.matcher.OrRequestMatcher

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val authenticationService: AuthenticationService,
    private val lastSeenFilter: LastSeenFilter,
) {
    @Bean
    fun userDetailsService(): UserDetailsService =
        UserDetailsService { username ->
            authenticationService.loadPrincipal(username.toLong())
                ?: throw IllegalArgumentException("Unknown user: $username")
        }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/",
                    "/live/",
                    "/timeline",
                    "/halloffame",
                    "/changelog",
                    "/login",
                    "/login_teamspeak",
                    "/link_auth",
                    "/logout",
                    "/live_fragment",
                    "/activity_fragment",
                    "/recent_events_fragment",
                    "/api/v1/live",
                    "/api/v1/widget",
                    "/widget_legacy",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/favicon*",
                    "/main.css",
                    "/main.js",
                    "/styles.css",
                    "/theme.js",
                    "/loading_oval.svg",
                    "/quitting_time.svg",
                    "/spybot_ai_icon.png",
                    "/tabler-sprite.svg",
                ).permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/passkeys/generate-authentication-options", "/passkeys/verify-authentication").permitAll()
                    .requestMatchers("/u/*", "/profile", "/profile/**", "/passkeys/generate-registration-options", "/passkeys/verify-registration").authenticated()
                    .anyRequest().permitAll()
            }.csrf {
                it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers("/passkeys/**")
            }.logout {
                it.logoutRequestMatcher(pathPattern(HttpMethod.GET, "/logout"))
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            }.exceptionHandling {
                val jsonEndpoints =
                    OrRequestMatcher(
                        pathPattern("/passkeys/generate-registration-options"),
                        pathPattern("/passkeys/verify-registration"),
                    )
                it.defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), jsonEndpoints)
                    .defaultAuthenticationEntryPointFor(LoginUrlAuthenticationEntryPoint("/login"), pathPattern("/**"))
            }
            .addFilterAfter(lastSeenFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter::class.java)

        return http.build()
    }
}
