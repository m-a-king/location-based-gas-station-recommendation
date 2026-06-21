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
    fun securityFilterChain(
        http: HttpSecurity,
        oAuthUserService: OAuthUserService,
        appJwtProvider: AppJwtProvider
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it
                    // 추천·배치는 로그인 사용자만 — 추천은 연비·유종을 프로필에서 읽고,
                    // 배치(/api/batch/**)는 외부 OPINET 다운로드·Kakao 지오코딩·DB 대량 쓰기를 유발하므로
                    // 무인증 노출 시 누구나 DoS·외부 쿼터 소진·데이터 훼손이 가능해 인증 필수.
                    // (구체 패턴이라 아래 /api/** permitAll보다 먼저 평가돼야 한다.)
                    .requestMatchers(
                        "/api/gas-stations/recommendations/**",
                        "/api/batch/**"
                    ).authenticated()
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
                    // 카카오 ID 토큰을 그대로 넘기지 않고, 카카오 sub로 자체 JWT를 발급해 전달한다.
                    val oidcUser = authentication.principal as OidcUser
                    val token = appJwtProvider.issue(oidcUser.subject)
                    response.sendRedirect("$frontendUrl/oauth/callback?token=$token")
                }
            }
            // Bearer 검증은 카카오 JWKS가 아니라 JwtConfig의 자체 JwtDecoder(우리 secret)를 사용한다.
            .oauth2ResourceServer { rs ->
                rs.jwt { }
            }
            .logout {
                it.logoutSuccessUrl("/").permitAll()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }

        return http.build()
    }
}
