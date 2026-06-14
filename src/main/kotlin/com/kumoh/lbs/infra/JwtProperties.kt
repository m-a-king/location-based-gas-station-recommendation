package com.kumoh.lbs.infra

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 자체 발급 JWT 설정. 카카오 OIDC로 최초 신원 확인 후, 이후 인증은 이 토큰으로 처리한다.
 * 리프레시 토큰은 두지 않으므로 expiration이 곧 재로그인 주기다.
 *
 * secret은 HS256 서명 키로, 운영에서는 반드시 JWT_SECRET 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val expiration: Duration = Duration.ofDays(7)
) {
    init {
        require(secret.toByteArray().size >= 32) {
            "JWT secret은 HS256 기준 최소 32바이트(256비트)여야 합니다: 현재 ${secret.toByteArray().size}바이트"
        }
    }
}
