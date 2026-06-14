package com.kumoh.lbs.auth

import com.kumoh.lbs.infra.JwtProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 카카오 로그인으로 확인된 사용자에게 자체 JWT를 발급한다.
 * subject에 카카오 sub를 그대로 담아, 기존 CurrentUserProvider의 사용자 조회와 호환된다.
 */
@Component
class AppJwtProvider(
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties
) {

    fun issue(kakaoSub: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(kakaoSub)
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.expiration))
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}
