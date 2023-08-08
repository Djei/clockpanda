package djei.clockpanda.authnz

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@Configuration
class WebSecurityConfiguration {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { a ->
                a
                    .requestMatchers(
                        AntPathRequestMatcher.antMatcher("/"),
                        AntPathRequestMatcher.antMatcher("/index.html"),
                        AntPathRequestMatcher.antMatcher("/favicon.ico"),
                        AntPathRequestMatcher.antMatcher("/webjars/**"),
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { e ->
                e.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            // TODO fix CSRF
            .csrf { c ->
                c.disable()
            }
            .oauth2Login { o ->
                o.userInfoEndpoint { u ->
                    u.oidcUserService(ClockPandaOidcUserService())
                }
            }
            .logout { l ->
                l.logoutSuccessUrl("/").permitAll()
            }
        return http.build()
    }
}
