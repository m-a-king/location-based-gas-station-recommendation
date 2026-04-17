package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.infra.GlobalExceptionHandler
import com.kumoh.lbs.gasstation.service.GasStationRadiusRecommender
import com.kumoh.lbs.gasstation.service.GasStationRouteRecommender
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * 컨트롤러 validation 격리 테스트 (@WebMvcTest 슬라이스).
 *
 * 모킹 이유: 이 테스트는 컨트롤러 계층의 @RequestParam 검증(@DecimalMin, @Max, @Positive, 유종 enum)이
 * 서비스 호출 이전에 400으로 단락되는지만 검증한다. 서비스 빈을 실 구동하면 검증 관심사가 흐려지고
 * 테스트 부팅이 무거워지므로 recommender들은 MockitoBean으로 두되 호출은 하지 않는다.
 * 서비스·DB까지 포함된 흐름은 GasStationE2eTest / RouteGasStationE2eTest에서 검증한다.
 */
@WebMvcTest(GasStationController::class)
@Import(GlobalExceptionHandler::class)
class GasStationControllerTest(
    val mockMvc: MockMvc,
    @MockitoBean val radiusRecommender: GasStationRadiusRecommender,
    @MockitoBean val routeRecommender: GasStationRouteRecommender
) {

    @Test
    fun `좌표 없이 요청하면 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/radius") {
            param("radius", "5000")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { exists() }
        }
    }

    @Test
    fun `limit이 3을 초과하면 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/radius") {
            param("latitude", "37.0")
            param("longitude", "127.0")
            param("radius", "5000")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "10")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `지원하지 않는 유종 코드는 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/radius") {
            param("latitude", "37.0")
            param("longitude", "127.0")
            param("radius", "5000")
            param("fuelType", "INVALID")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ─── 경로 기반 API validation ─────────────────────────────────────────────

    @Test
    fun `경로 API에서 필수 파라미터 누락 시 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/route") {
            // originLatitude 누락
            param("originLongitude", "127.0")
            param("destinationLatitude", "35.1")
            param("destinationLongitude", "129.0")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `경로 API에서 지원하지 않는 유종 코드는 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLatitude", "37.0")
            param("originLongitude", "127.0")
            param("destinationLatitude", "35.1")
            param("destinationLongitude", "129.0")
            param("fuelType", "INVALID")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `경로 API에서 limit이 3을 초과하면 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLatitude", "37.0")
            param("originLongitude", "127.0")
            param("destinationLatitude", "35.1")
            param("destinationLongitude", "129.0")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "10")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
