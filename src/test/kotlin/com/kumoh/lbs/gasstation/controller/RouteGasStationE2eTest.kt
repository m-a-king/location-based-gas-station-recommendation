package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.TestcontainersConfiguration
import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.GasStationPrice
import com.kumoh.lbs.gasstation.domain.GasStationPriceId
import com.kumoh.lbs.gasstation.domain.Route
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import com.kumoh.lbs.geo.Coordinate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import kotlin.math.abs

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class RouteGasStationE2eTest(
    val mockMvc: MockMvc,
    @MockitoBean val kakaoDirectionsClient: KakaoDirectionsClient,
    @Autowired val gasStationRepository: GasStationRepository,
    @Autowired val gasStationPriceRepository: GasStationPriceRepository
) {

    private val baseRoute = route(distanceMeters = 15000)

    // 폴리라인: (37.0, 127.0) → (37.05, 127.05) → (37.1, 127.1)
    // 모든 픽스처는 폴리라인으로부터 2000m 이내
    private val onRouteStation         = gasStation("ON_ROUTE",  "경로위주유소",    lat = 37.05,  lon = 127.05)
    private val cheapOffRouteStation   = gasStation("OFF_ROUTE", "경로밖싼주유소",  lat = 37.065, lon = 127.05)
    private val expensiveOnRouteStation = gasStation("EXPENSIVE", "경로위비싼주유소", lat = 37.03, lon = 127.03)

    @BeforeEach
    fun setUp() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
    }

    @AfterEach
    fun tearDown() {
        gasStationPriceRepository.deleteAll()
        gasStationRepository.deleteAll()
    }

    @Test
    fun `경로 기반 추천은 실제 우회 거리를 반영하여 점수를 매긴다`() {
        saveStations(onRouteStation, cheapOffRouteStation, expensiveOnRouteStation)
        savePrices(mapOf("ON_ROUTE" to 1650, "OFF_ROUTE" to 1500, "EXPENSIVE" to 1900))

        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.05, 127.05)))
            .thenReturn(route(distanceMeters = 15200))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.065, 127.05)))
            .thenReturn(route(distanceMeters = 21000))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), coordAt(37.03, 127.03)))
            .thenReturn(route(distanceMeters = 15100))

        // 점수 계산 (40L, 10km/L):
        // OFF_ROUTE:  1500×40 + (6000/1000/10×1500) = 60000 + 900  = 60900 (1위)
        // ON_ROUTE:   1650×40 + ( 200/1000/10×1650) = 66000 +  33  = 66033 (2위)
        // EXPENSIVE:  1900×40 + ( 100/1000/10×1900) = 76000 +  19  = 76019 (3위)
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
    fun `경유 경로 조회 실패 시 500을 반환한다`() {
        saveStations(onRouteStation)
        savePrices(mapOf("ON_ROUTE" to 1650))

        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any())).thenReturn(null)

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
            status { isInternalServerError() }
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
        saveStations(onRouteStation)
        savePrices(mapOf("ON_ROUTE" to 1650))
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
            param("limit", "3")
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
        saveStations(onRouteStation)
        savePrices(mapOf("ON_ROUTE" to 1650))
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

    private fun saveStations(vararg stations: GasStation) {
        gasStationRepository.saveAll(stations.toList())
    }

    private fun savePrices(priceByStationId: Map<String, Int>) {
        val prices = priceByStationId.map { (id, price) ->
            GasStationPrice(
                id = GasStationPriceId(stationId = id, fuelType = FuelType.GASOLINE),
                station = gasStationRepository.findById(id).orElseThrow(),
                price = price,
                updatedAt = LocalDate.now()
            )
        }
        gasStationPriceRepository.saveAll(prices)
    }

    private fun coordAt(lat: Double, lon: Double): Coordinate =
        argThat { abs(wgs84.latitude - lat) < 0.001 && abs(wgs84.longitude - lon) < 0.001 }

    private fun gasStation(id: String, name: String, lat: Double, lon: Double) =
        GasStation(id = id, name = name, brand = "SKE", latitude = lat, longitude = lon)

    private fun route(distanceMeters: Int, coordinates: List<Coordinate>? = null): Route {
        val polyline = coordinates ?: listOf(
            Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.05, 127.05)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.1, 127.1))
        )
        return Route(polyline = polyline, distanceMeters = distanceMeters)
    }
}
