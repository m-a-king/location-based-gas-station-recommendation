package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.client.OpinetClient
import com.kumoh.lbs.geo.Coordinate
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.NearbyStation
import com.kumoh.lbs.gasstation.domain.Route
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

    private val origin = Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0))
    private val destination = Coordinate.fromWgs84(Coordinate.Wgs84(37.1, 127.1))
    private val baseRoute = route(distanceMeters = 15000)

    private val onRouteStation = nearbyStation(
        id = "ON_ROUTE", name = "경로위주유소", brandCode = "SKE",
        lat = 37.05, lon = 127.05, price = 1650, distanceMeters = 500.0
    )
    private val offRouteStation = nearbyStation(
        id = "OFF_ROUTE", name = "경로밖싼주유소", brandCode = "GSC",
        lat = 37.08, lon = 127.0, price = 1500, distanceMeters = 3000.0
    )
    private val expensiveOnRouteStation = nearbyStation(
        id = "EXPENSIVE", name = "경로위비싼주유소", brandCode = "HDO",
        lat = 37.03, lon = 127.03, price = 1900, distanceMeters = 400.0
    )

    @Test
    fun `경로 기반 추천은 실제 우회 거리를 반영하여 점수를 매긴다`() {
        stubBaseRoute()
        stubOpinetReturns(listOf(onRouteStation, offRouteStation, expensiveOnRouteStation))

        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.05, 127.05)))
            .thenReturn(route(distanceMeters = 15200))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.08, 127.0)))
            .thenReturn(route(distanceMeters = 21000))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.03, 127.03)))
            .thenReturn(route(distanceMeters = 15100))

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
            jsonPath("$[0].opinetStationId") { value("OFF_ROUTE") }
            jsonPath("$[1].opinetStationId") { value("ON_ROUTE") }
            jsonPath("$[2].opinetStationId") { value("EXPENSIVE") }
        }
    }

    @Test
    fun `경유 경로 조회 실패 시 1차 직선거리 점수로 대체한다`() {
        stubBaseRoute()
        stubOpinetReturns(listOf(onRouteStation, offRouteStation))

        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.05, 127.05)))
            .thenReturn(route(distanceMeters = 15200))
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
            .thenReturn(route(distanceMeters = 15300))

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
            .thenReturn(route(distanceMeters = 15200))

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

    private fun stubBaseRoute() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
    }

    private fun stubOpinetReturns(stations: List<NearbyStation>) {
        whenever(opinetClient.searchByRadius(any(), any(), any(), any())).thenReturn(stations)
    }

    private fun coordAt(lat: Double, lon: Double): Coordinate =
        argThat { abs(wgs84.latitude - lat) < 0.001 && abs(wgs84.longitude - lon) < 0.001 }

    private fun nearbyStation(
        id: String, name: String, brandCode: String,
        lat: Double, lon: Double, price: Int, distanceMeters: Double
    ): NearbyStation = NearbyStation(
        station = GasStation(id = id, name = name, brand = brandCode, latitude = lat, longitude = lon),
        price = price,
        distanceMeters = distanceMeters
    )

    private fun route(distanceMeters: Int, coordinates: List<Coordinate>? = null): Route {
        val polyline = coordinates ?: listOf(
            Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.05, 127.05)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.1, 127.1))
        )
        return Route(polyline = polyline, distanceMeters = distanceMeters)
    }
}
