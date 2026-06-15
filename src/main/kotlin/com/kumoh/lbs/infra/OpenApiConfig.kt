package com.kumoh.lbs.infra

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

/**
 * Swagger UI의 "Authorize" 버튼(Bearer JWT)을 활성화하고,
 * Swagger에 노출되지 않는 로그인/토큰 발급 흐름을 상단 설명으로 안내한다.
 * 인증이 필요한 컨트롤러는 @SecurityRequirement(name = "bearerAuth")로 자물쇠를 표시한다.
 */
@OpenAPIDefinition(
    info = Info(
        title = "LBS 주유소 추천 API",
        version = "v1",
        description = """
            가격·우회 비용을 함께 고려한 주유소 추천 LBS API.

            ## 로그인 / 토큰 발급 (이 흐름은 Swagger 경로 목록에 없음 — Spring Security가 처리)
            1. 브라우저에서 `/oauth2/authorization/kakao` 접속 → 카카오 로그인
            2. 성공 시 `{프론트엔드}/oauth/callback?token=<자체 JWT>`로 리다이렉트
            3. 그 `token` 값을 우측 상단 **Authorize**에 입력하면 🔒 표시된 인증 API를 시험할 수 있음

            토큰은 7일 만료(리프레시 없음). **추천 API는 로그인 + 차량 프로필(연비·유종, `PUT /users/me`) 입력이 선행돼야 하며, 미입력 시 400을 반환**한다.
        """
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "카카오 로그인 후 발급받은 자체 JWT를 입력하세요."
)
@Configuration
class OpenApiConfig
