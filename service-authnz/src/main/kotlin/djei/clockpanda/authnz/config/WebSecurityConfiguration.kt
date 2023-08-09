package djei.clockpanda.authnz.config

import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@Configuration
class WebSecurityConfiguration(
    private val clockPandaAuthorizationRequestResolver: ClockPandaAuthorizationRequestResolver,
    private val clockPandaAccessTokenResponseClient: ClockPandaAccessTokenResponseClient,
    private val clockPandaAuthorizedClientRepository: ClockPandaAuthorizedClientRepository
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { a ->
                a
                    // https://docs.spring.io/spring-security/reference/5.8/migration/servlet/authorization.html#_permit_forward_when_using_spring_mvc
                    .dispatcherTypeMatchers(DispatcherType.FORWARD).permitAll()
                    // Paths that can be accessed without any authentication
                    .requestMatchers(
                        AntPathRequestMatcher.antMatcher("/"),
                        AntPathRequestMatcher.antMatcher("/favicon.ico"),
                        AntPathRequestMatcher.antMatcher("/webjars/**")
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { e ->
                e.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .oauth2Login { o ->
                o
                    // Customize authorization endpoint generator because we need to add `access_type=offline` to get a refresh token for background processing on the user calendar
                    .authorizationEndpoint { a ->
                        a.authorizationRequestResolver(clockPandaAuthorizationRequestResolver)
                    }
                    // Customize token endpoint to validate scopes granted by the user to our tokens
                    .tokenEndpoint { t ->
                        t.accessTokenResponseClient(clockPandaAccessTokenResponseClient)
                    }
                    // Customize authorized client repository to register user in our own database with its refresh token
                    .authorizedClientRepository(clockPandaAuthorizedClientRepository)
            }
            .logout { l ->
                l.logoutSuccessUrl("/").permitAll()
            }
        return http.build()
    }
}
