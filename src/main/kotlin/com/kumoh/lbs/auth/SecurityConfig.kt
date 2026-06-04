package com.kumoh.lbs.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

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
                it.defaultSuccessUrl("/auth/me", true)
            }
            .logout {
                it.logoutSuccessUrl("/").permitAll()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }

        return http.build()
    }
}
