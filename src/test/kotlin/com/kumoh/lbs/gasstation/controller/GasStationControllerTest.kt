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
    fun `limit 범위를 초과하면 400을 반환한다`() {
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
}
