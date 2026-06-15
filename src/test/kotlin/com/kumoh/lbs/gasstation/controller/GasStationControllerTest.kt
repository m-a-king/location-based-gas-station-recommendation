package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.auth.CurrentUserProvider
import com.kumoh.lbs.auth.User
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.infra.GlobalExceptionHandler
import com.kumoh.lbs.gasstation.service.GasStationRadiusRecommender
import com.kumoh.lbs.gasstation.service.GasStationRouteRecommender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * 컨트롤러 validation 격리 테스트 (@WebMvcTest 슬라이스).
 *
 * 모킹 이유: 이 테스트는 컨트롤러 계층의 @RequestParam 검증(@DecimalMin, @Max, @Positive)이
 * 서비스 호출 이전에 400으로 단락되는지만 검증한다. 서비스 빈을 실 구동하면 검증 관심사가 흐려지고
 * 테스트 부팅이 무거워지므로 recommender들은 MockitoBean으로 두되 호출은 하지 않는다.
 * 추천 API는 인증 필수이므로 jwt()로 인증하고, 유종·연비는 CurrentUserProvider가 반환하는 프로필에서 온다.
 * 서비스·DB까지 포함된 흐름은 GasStationE2eTest / RouteGasStationE2eTest에서 검증한다.
 */
@WebMvcTest(
    controllers = [GasStationController::class],
    excludeAutoConfiguration = [
        OAuth2ClientAutoConfiguration::class,
        OAuth2ClientWebSecurityAutoConfiguration::class
    ]
)
@Import(GlobalExceptionHandler::class)
class GasStationControllerTest(
    val mockMvc: MockMvc,
    @MockitoBean val radiusRecommender: GasStationRadiusRecommender,
    @MockitoBean val routeRecommender: GasStationRouteRecommender,
    @MockitoBean val currentUserProvider: CurrentUserProvider
) {

    @BeforeEach
    fun setUp() {
        whenever(currentUserProvider.resolve(any()))
            .thenReturn(User(kakaoSub = "test-user", fuelType = FuelType.GASOLINE, fuelEfficiency = 10.0))
    }

    @Test
    fun `좌표 없이 요청하면 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/radius") {
            with(jwt().jwt { it.subject("test-user") })
            param("radius", "5000")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { exists() }
        }
    }

    @Test
    fun `limit이 3을 초과하면 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/radius") {
            with(jwt().jwt { it.subject("test-user") })
            param("latitude", "37.0")
            param("longitude", "127.0")
            param("radius", "5000")
            param("refuelLiters", "40.0")
            param("limit", "10")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // 미인증 401은 전체 시큐리티 체인이 도는 E2E(GasStation/RouteGasStationE2eTest)에서 검증한다.
    // 이 슬라이스는 SecurityConfig를 로드하지 않으므로 validation 단락만 다룬다.

    // ─── 경로 기반 API validation ─────────────────────────────────────────────

    @Test
    fun `경로 API에서 필수 파라미터 누락 시 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/route") {
            with(jwt().jwt { it.subject("test-user") })
            // originLatitude 누락
            param("originLongitude", "127.0")
            param("destinationLatitude", "35.1")
            param("destinationLongitude", "129.0")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `경로 API에서 limit이 3을 초과하면 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/route") {
            with(jwt().jwt { it.subject("test-user") })
            param("originLatitude", "37.0")
            param("originLongitude", "127.0")
            param("destinationLatitude", "35.1")
            param("destinationLongitude", "129.0")
            param("refuelLiters", "40.0")
            param("limit", "10")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
