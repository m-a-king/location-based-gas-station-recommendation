package com.kumoh.lbs.controller

import com.kumoh.lbs.client.KakaoDirectionsClient
import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetStation
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.Route
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.math.abs

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteGasStationE2eTest(
    val mockMvc: MockMvc,
    @MockitoBean val opinetClient: OpinetClient,
    @MockitoBean val kakaoDirectionsClient: KakaoDirectionsClient
) {

    // 서울(127.0, 37.0) → 수원(127.1, 37.1) 약 15km 경로 시뮬레이션
    private val origin = Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0))
    private val destination = Coordinate.fromWgs84(Coordinate.Wgs84(37.1, 127.1))

    // 기본 경로: 15km, 폴리라인 3개 좌표 (직선)
    private val baseRoute = route(distance = 15000)

    // 경로 위 주유소 (경로에 가까움)
    private val onRouteStation = opinetStation(
        id = "ON_ROUTE", name = "경로위주유소", brandCode = "SKE",
        lat = 37.05, lon = 127.05, price = 1650, distance = 500.0
    )

    // 경로에서 먼 주유소 (싸지만 멀리 우회)
    private val offRouteStation = opinetStation(
        id = "OFF_ROUTE", name = "경로밖싼주유소", brandCode = "GSC",
        lat = 37.08, lon = 127.0, price = 1500, distance = 3000.0
    )

    // 경로 위 비싼 주유소
    private val expensiveOnRouteStation = opinetStation(
        id = "EXPENSIVE", name = "경로위비싼주유소", brandCode = "HDO",
        lat = 37.03, lon = 127.03, price = 1900, distance = 400.0
    )

    @Test
    fun `경로 기반 추천은 실제 우회 거리를 반영하여 점수를 매긴다`() {
        stubBaseRoute()
        stubOpinetReturns(listOf(onRouteStation, offRouteStation, expensiveOnRouteStation))

        // 경유 경로 거리: 경로 위 주유소 = 15200m (추가 200m), 경로 밖 주유소 = 21000m (추가 6000m), 비싼 = 15100m
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.05, 127.05)))
            .thenReturn(route(distance = 15200))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.08, 127.0)))
            .thenReturn(route(distance = 21000))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.03, 127.03)))
            .thenReturn(route(distance = 15100))

        // refuelLiters=40, fuelEfficiency=10
        // onRoute:   1650*40 + (200/1000/10)*1650  = 66000 + 33    = 66033
        // offRoute:  1500*40 + (6000/1000/10)*1500  = 60000 + 900   = 60900
        // expensive: 1900*40 + (100/1000/10)*1900   = 76000 + 19    = 76019

        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLongitude", "127.0")
            param("originLatitude", "37.0")
            param("destinationLongitude", "127.1")
            param("destinationLatitude", "37.1")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            // 가격 싸고 우회 많은 offRoute가 1위 (60900)
            jsonPath("$[0].opinetStationId") { value("OFF_ROUTE") }
            // 경로 위 저렴한 onRoute가 2위 (66033)
            jsonPath("$[1].opinetStationId") { value("ON_ROUTE") }
            // 경로 위지만 비싼 expensive가 3위 (76019)
            jsonPath("$[2].opinetStationId") { value("EXPENSIVE") }
        }
    }

    @Test
    fun `경유 경로 조회 실패 시 1차 직선거리 점수로 대체한다`() {
        stubBaseRoute()
        stubOpinetReturns(listOf(onRouteStation, offRouteStation))

        // onRoute: waypoint 조회 성공
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.05, 127.05)))
            .thenReturn(route(distance = 15200))
        // offRoute: waypoint 조회 실패 → null → 1차 직선거리 점수 유지
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.08, 127.0)))
            .thenReturn(null)

        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLongitude", "127.0")
            param("originLatitude", "37.0")
            param("destinationLongitude", "127.1")
            param("destinationLatitude", "37.1")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            // 두 주유소 모두 결과에 포함됨 (fallback 동작)
            jsonPath("$[0].score") { exists() }
            jsonPath("$[1].score") { exists() }
        }
    }

    @Test
    fun `경로 조회 실패 시 500을 반환한다`() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(null)

        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLongitude", "127.0")
            param("originLatitude", "37.0")
            param("destinationLongitude", "127.1")
            param("destinationLatitude", "37.1")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.detail") { value("경로를 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `경로 상 주유소가 없으면 빈 배열을 반환한다`() {
        stubBaseRoute()
        stubOpinetReturns(emptyList())

        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLongitude", "127.0")
            param("originLatitude", "37.0")
            param("destinationLongitude", "127.1")
            param("destinationLatitude", "37.1")
            param("fuelType", "DIESEL")
            param("refuelLiters", "30.0")
            param("fuelEfficiency", "15.0")
            param("limit", "3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `limit보다 주유소가 적으면 있는 만큼만 반환한다`() {
        stubBaseRoute()
        stubOpinetReturns(listOf(onRouteStation))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(route(distance = 15300))

        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLongitude", "127.0")
            param("originLatitude", "37.0")
            param("destinationLongitude", "127.1")
            param("destinationLatitude", "37.1")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "5")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].opinetStationId") { value("ON_ROUTE") }
        }
    }

    @Test
    fun `필수 파라미터가 누락되면 400을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLongitude", "127.0")
            // originLatitude 누락
            param("destinationLongitude", "127.1")
            param("destinationLatitude", "37.1")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `응답에 좌표와 점수가 포함된다`() {
        stubBaseRoute()
        stubOpinetReturns(listOf(onRouteStation))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(route(distance = 15200))

        mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLongitude", "127.0")
            param("originLatitude", "37.0")
            param("destinationLongitude", "127.1")
            param("destinationLatitude", "37.1")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].latitude") { isNumber() }
            jsonPath("$[0].longitude") { isNumber() }
            jsonPath("$[0].price") { value(1650) }
            jsonPath("$[0].score") { isNumber() }
        }
    }

    // --- Helper functions ---

    private fun stubBaseRoute() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
    }

    private fun stubOpinetReturns(stations: List<OpinetStation>) {
        whenever(opinetClient.searchByRadius(any(), any(), any(), any())).thenReturn(stations)
    }

    /** WGS84 좌표 기준으로 Coordinate 인자를 매칭 (부동소수점 오차 허용) */
    private fun coordAt(lat: Double, lon: Double): Coordinate =
        argThat { abs(wgs84.latitude - lat) < 0.001 && abs(wgs84.longitude - lon) < 0.001 }

    private fun opinetStation(
        id: String,
        name: String,
        brandCode: String,
        lat: Double,
        lon: Double,
        price: Int,
        distance: Double
    ): OpinetStation {
        val katec = Coordinate.fromWgs84(Coordinate.Wgs84(lat, lon)).katec
        return OpinetStation(
            stationId = id,
            stationName = name,
            brandCode = brandCode,
            price = price,
            distance = distance,
            katecX = katec.x,
            katecY = katec.y
        )
    }

    private fun route(distance: Int, coordinates: List<Coordinate>? = null): Route {
        val polyline = coordinates ?: listOf(
            Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.05, 127.05)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.1, 127.1))
        )
        return Route(polyline = polyline, distanceMeters = distance)
    }
}
