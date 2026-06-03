package com.kumoh.lbs.auth

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: OidcUser): Map<String, Any?> =
        mapOf(
            "authenticated" to true,
            "sub" to principal.subject,
            "issuer" to principal.issuer?.toString(),
            "issuedAt" to principal.issuedAt?.toString(),
            "expiresAt" to principal.expiresAt?.toString(),
            "claims" to principal.claims
        )

    @GetMapping("/login")
    fun loginHint(): Map<String, String> =
        mapOf(
            "loginUrl" to "/oauth2/authorization/kakao",
            "note" to "브라우저에서 위 경로로 이동하면 카카오 로그인이 시작됩니다."
        )
}
