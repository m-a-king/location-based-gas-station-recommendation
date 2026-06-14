package com.kumoh.lbs.auth

import com.kumoh.lbs.infra.JwtProperties
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.spec.SecretKeySpec
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 자체 JWT 발급·검증 단위 테스트. 같은 비밀키로 만든 encoder/decoder만 사용하므로
 * Spring 컨텍스트나 DB 없이 빠르게 라운드트립과 만료 거부를 검증한다.
 */
class AppJwtProviderTest {

    private val secret = "lbs-test-secret-key-at-least-32-bytes-long-0123456789"
    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    private val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))
    private val decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()

    @Test
    fun `발급한 토큰은 우리 디코더로 검증되고 subject에 카카오 sub가 보존된다`() {
        val provider = AppJwtProvider(encoder, JwtProperties(secret, Duration.ofDays(7)))

        val token = provider.issue("kakao-123")
        val jwt = decoder.decode(token)

        assertEquals("kakao-123", jwt.subject)
        // 7일 만료가 반영됐는지: 지금으로부터 6일 뒤보다는 더 미래여야 한다.
        assertTrue(jwt.expiresAt!!.isAfter(Instant.now().plus(Duration.ofDays(6))))
    }

    @Test
    fun `만료된 토큰은 검증에 실패한다`() {
        // 디코더의 기본 clock skew(60초)를 넘기도록 이미 지난 만료 토큰을 직접 인코딩한다.
        val past = Instant.now().minusSeconds(600)
        val claims = JwtClaimsSet.builder()
            .subject("kakao-123")
            .issuedAt(past)
            .expiresAt(past.plusSeconds(60))
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue

        assertThrows<JwtException> { decoder.decode(token) }
    }

    @Test
    fun `다른 비밀키로 발급된 토큰은 검증에 실패한다`() {
        val otherKey = SecretKeySpec("another-secret-key-also-32-bytes-minimum-987654321".toByteArray(), "HmacSHA256")
        val otherEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(otherKey))
        val provider = AppJwtProvider(otherEncoder, JwtProperties(secret, Duration.ofDays(7)))

        val token = provider.issue("kakao-123")

        assertThrows<JwtException> { decoder.decode(token) }
    }
}
