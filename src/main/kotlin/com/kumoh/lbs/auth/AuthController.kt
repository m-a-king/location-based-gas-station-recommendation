package com.kumoh.lbs.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증", description = "카카오 로그인 시작 안내 · 현재 토큰 정보")
@RestController
@RequestMapping("/auth")
class AuthController {

    @Operation(
        summary = "현재 로그인 정보",
        description = "Bearer 토큰의 sub·발급/만료 시각·클레임을 반환합니다. 토큰 유효성 확인용."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: Any): Map<String, Any?> =
        when (principal) {
            is OidcUser -> mapOf(
                "authenticated" to true,
                "sub" to principal.subject,
                "issuer" to principal.issuer?.toString(),
                "issuedAt" to principal.issuedAt?.toString(),
                "expiresAt" to principal.expiresAt?.toString(),
                "claims" to principal.claims
            )
            is Jwt -> mapOf(
                "authenticated" to true,
                "sub" to principal.subject,
                "issuer" to principal.issuer?.toString(),
                "issuedAt" to principal.issuedAt?.toString(),
                "expiresAt" to principal.expiresAt?.toString(),
                "claims" to principal.claims
            )
            else -> mapOf("authenticated" to false)
        }

    @Operation(
        summary = "로그인 시작 URL 안내",
        description = "카카오 로그인 시작 경로를 안내합니다. 실제 로그인은 브라우저에서 /oauth2/authorization/kakao 로 이동해야 합니다(API 호출이 아님)."
    )
    @GetMapping("/login")
    fun loginHint(): Map<String, String> =
        mapOf(
            "loginUrl" to "/oauth2/authorization/kakao",
            "note" to "브라우저에서 위 경로로 이동하면 카카오 로그인이 시작됩니다."
        )
}
