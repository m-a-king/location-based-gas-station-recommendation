package com.kumoh.lbs.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Value("\${frontend.url:http://localhost:5173}")
    private lateinit var frontendUrl: String

    @Bean
    fun securityFilterChain(http: HttpSecurity, oAuthUserService: OAuthUserService): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/",
                        "/error",
                        "/api/**",
                        "/login/**",
                        "/oauth2/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                    ).permitAll()
                    .requestMatchers("/auth/me", "/users/**").authenticated()
                    .anyRequest().permitAll()
            }
            .oauth2Login {
                it.userInfoEndpoint { userInfo -> userInfo.oidcUserService(oAuthUserService) }
                it.successHandler { _, response, authentication ->
                    val oidcUser = authentication.principal as OidcUser
                    val token = oidcUser.idToken.tokenValue
                    response.sendRedirect("$frontendUrl/oauth/callback?token=$token")
                }
            }
            .oauth2ResourceServer { rs ->
                rs.jwt { jwt ->
                    jwt.jwkSetUri("https://kauth.kakao.com/.well-known/jwks.json")
                }
            }
            .logout {
                it.logoutSuccessUrl("/").permitAll()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }

        return http.build()
    }
}
