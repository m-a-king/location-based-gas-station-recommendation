package com.kumoh.lbs.auth

import com.kumoh.lbs.TestcontainersConfiguration
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import javax.crypto.spec.SecretKeySpec
import java.time.Instant

/**
 * 자체 발급 JWT가 실제 시큐리티 필터 체인(resourceServer + 우리 JwtDecoder)을 통과해
 * 보호 엔드포인트에 닿는지 전 구간을 검증한다. jwt() post-processor와 달리 디코더를 우회하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Transactional
class JwtAuthE2eTest(
    val mockMvc: MockMvc,
    val appJwtProvider: AppJwtProvider,
    val userRepository: UserRepository
) {

    @Test
    fun `자체 발급 JWT로 보호 엔드포인트에 접근하면 200을 반환한다`() {
        userRepository.save(User(kakaoSub = KAKAO_SUB))
        val token = appJwtProvider.issue(KAKAO_SUB)

        mockMvc.get("/users/me/favorites") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `다른 키로 위조한 토큰은 401을 반환한다`() {
        val forgedKey = SecretKeySpec("forged-attacker-key-also-32-bytes-minimum-xyz".toByteArray(), "HmacSHA256")
        val forgedEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(forgedKey))
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(KAKAO_SUB)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build()
        val forged = forgedEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue

        mockMvc.get("/users/me/favorites") {
            header("Authorization", "Bearer $forged")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `깨진 형식의 Bearer 토큰은 401을 반환한다`() {
        mockMvc.get("/users/me/favorites") {
            header("Authorization", "Bearer not-a-real-jwt")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `인증 없이 배치 import를 호출하면 401을 반환한다`() {
        // 무인증 호출은 시큐리티 필터에서 401로 막혀 컨트롤러(실제 OPINET 다운로드)에 닿지 않는다.
        // /api/batch/** 가 /api/** permitAll보다 먼저 authenticated()로 평가되는지 회귀 검증.
        mockMvc.post("/api/batch/opinet/import")
            .andExpect { status { isUnauthorized() } }
    }

    companion object {
        private const val KAKAO_SUB = "kakao-real-jwt-test"
    }
}
