package com.kumoh.lbs.infra

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

/**
 * Swagger UI의 "Authorize" 버튼(Bearer JWT)을 활성화한다.
 * 인증이 필요한 컨트롤러는 @SecurityRequirement(name = "bearerAuth")로 자물쇠를 표시한다.
 */
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "카카오 로그인 후 발급받은 자체 JWT를 입력하세요."
)
@Configuration
class OpenApiConfig
