package com.spybot.web.config

import com.spybot.core.config.SpybotProperties
import com.spybot.core.service.AuthenticationService
import com.spybot.web.filter.LastSeenFilter
import com.spybot.web.security.MergedUserWebAuthnAuthenticationProvider
import com.spybot.web.security.WebauthnCredentialRepository
import com.spybot.web.security.WebauthnUserEntityRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.ObjectPostProcessor
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
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations
import java.net.URI

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val authenticationService: AuthenticationService,
    private val lastSeenFilter: LastSeenFilter,
    private val properties: SpybotProperties,
) {
    @Bean
    fun userDetailsService(): UserDetailsService =
        UserDetailsService { username ->
            authenticationService.loadPrincipal(username.toLong())
                ?: throw IllegalArgumentException("Unknown user: $username")
        }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /**
     * Passkeys (Spring Security WebAuthn). The RP id and the allowed origin both come from the
     * configured public URL, so they can't disagree - and the origin is not reconstructed from
     * proxy headers, which lie behind the production proxy chain (TLS ends before Caddy).
     */
    @Bean
    fun webAuthnRelyingPartyOperations(
        userEntities: WebauthnUserEntityRepository,
        credentials: WebauthnCredentialRepository,
    ): WebAuthnRelyingPartyOperations {
        val publicBaseUrl = URI(properties.publicBaseUrl)
        val relyingParty =
            PublicKeyCredentialRpEntity
                .builder()
                .id(publicBaseUrl.host)
                .name(properties.fidoServerName)
                .build()
        return Webauthn4JRelyingPartyOperations(userEntities, credentials, relyingParty, setOf(originOf(publicBaseUrl)))
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        relyingParty: WebAuthnRelyingPartyOperations,
        userDetailsService: UserDetailsService,
    ): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it
                    .requestMatchers(
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
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    // Passkey login (Spring Security WebAuthn): options + assertion are anonymous.
                    .requestMatchers("/webauthn/authenticate/options", "/login/webauthn")
                    .permitAll()
                    .requestMatchers(
                        "/u/*",
                        "/profile",
                        "/profile/**",
                        // Registering a passkey attaches it to the logged-in account.
                        "/webauthn/register/options",
                        "/webauthn/register",
                        "/webauthn/register/*",
                    ).authenticated()
                    .anyRequest()
                    .permitAll()
            }.csrf {
                // The WebAuthn endpoints are CSRF-protected like everything else; the browser code
                // sends the token from the XSRF-TOKEN cookie.
                it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            }.webAuthn {
                // Endpoints and session-bound challenges come from Spring; the relying party is
                // the webAuthnRelyingPartyOperations bean. The login filter gets a provider that
                // turns Spring's WebAuthnAuthentication into the app's MergedUserPrincipal.
                it
                    .disableDefaultRegistrationPage(true)
                    .withObjectPostProcessor(
                        object : ObjectPostProcessor<WebAuthnAuthenticationFilter> {
                            override fun <O : WebAuthnAuthenticationFilter> postProcess(filter: O): O {
                                val provider =
                                    MergedUserWebAuthnAuthenticationProvider(
                                        WebAuthnAuthenticationProvider(relyingParty, userDetailsService),
                                        authenticationService,
                                    )
                                filter.setAuthenticationManager(ProviderManager(provider))
                                return filter
                            }
                        },
                    )
            }.logout {
                it
                    .logoutRequestMatcher(pathPattern(HttpMethod.GET, "/logout"))
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            }.exceptionHandling {
                val jsonEndpoints =
                    OrRequestMatcher(
                        pathPattern("/webauthn/**"),
                        pathPattern("/login/webauthn"),
                    )
                it
                    .defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), jsonEndpoints)
                    .defaultAuthenticationEntryPointFor(LoginUrlAuthenticationEntryPoint("/login"), pathPattern("/**"))
            }.addFilterAfter(lastSeenFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter::class.java)

        return http.build()
    }

    private fun originOf(url: URI): String =
        buildString {
            append(url.scheme).append("://").append(url.host)
            if (url.port != -1) append(':').append(url.port)
        }
}
