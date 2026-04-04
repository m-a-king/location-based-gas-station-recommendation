package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.GasStationPrice
import com.kumoh.lbs.gasstation.domain.GasStationPriceId
import com.kumoh.lbs.gasstation.domain.Route
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import com.kumoh.lbs.geo.Coordinate
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class GasStationRouteRecommenderTest {

    @Mock lateinit var kakaoDirectionsClient: KakaoDirectionsClient
    @Mock lateinit var gasStationRepository: GasStationRepository
    @Mock lateinit var gasStationPriceRepository: GasStationPriceRepository

    @InjectMocks lateinit var recommender: GasStationRouteRecommender

    // 폴리라인: (37.0, 127.0) → (37.1, 127.0) 북쪽 직선
    private val polyline = listOf(
        wgs84(37.0, 127.0),
        wgs84(37.1, 127.0)
    )
    private val baseRoute = Route(polyline = polyline, distanceMeters = 11132)
    private val origin = wgs84(37.0, 127.0)
    private val destination = wgs84(37.1, 127.0)

    // ─── corridor 필터 ───────────────────────────────────────────────────────

    @Test
    fun `경로에서 2km 이상 떨어진 주유소는 후보에서 제외된다`() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        // 폴리라인(lon=127.0)에서 약 2.2km 동쪽 → corridor 밖
        val outsideStation = gasStation("OUT", lat = 37.05, lon = 127.025)
        val insideStation  = gasStation("IN",  lat = 37.05, lon = 127.015)

        whenever(gasStationRepository.findInBounds(any())).thenReturn(listOf(outsideStation, insideStation))
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any()))
            .thenReturn(listOf(price("IN", 1500)))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = 11500))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 5)

        result shouldHaveSize 1
        result[0].station.id shouldBe "IN"
    }

    // ─── 가격 없는 주유소 제외 ───────────────────────────────────────────────

    @Test
    fun `가격 정보가 없는 주유소는 후보에서 조용히 제외된다`() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        val withPrice    = gasStation("A", lat = 37.05, lon = 127.005)
        val withoutPrice = gasStation("B", lat = 37.05, lon = 127.005)

        whenever(gasStationRepository.findInBounds(any())).thenReturn(listOf(withPrice, withoutPrice))
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any()))
            .thenReturn(listOf(price("A", 1500)))  // B 가격 없음
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = 11500))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 5)

        result shouldHaveSize 1
        result[0].station.id shouldBe "A"
    }

    // ─── 1차 필터 limit * 3 ─────────────────────────────────────────────────

    @Test
    fun `2차 Kakao API 호출 수는 limit x3 이하다`() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        // corridor 안에 10개 주유소, limit=2 → 1차 필터 후 최대 6개만 2차 호출
        val stations = (1..10).map { gasStation("S$it", lat = 37.0 + it * 0.005, lon = 127.005) }
        val prices = stations.map { price(it.id, 1500 + stations.indexOf(it) * 10) }

        whenever(gasStationRepository.findInBounds(any())).thenReturn(stations)
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any())).thenReturn(prices)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = 11500))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 2)

        verify(kakaoDirectionsClient, times(6)).searchRouteViaWaypoint(any(), any(), any())
    }

    // ─── 실제 우회거리 음수 보정 ─────────────────────────────────────────────

    @Test
    fun `경유 경로가 기본 경로보다 짧게 나오면 우회거리는 0으로 보정된다`() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        val station = gasStation("A", lat = 37.05, lon = 127.005)

        whenever(gasStationRepository.findInBounds(any())).thenReturn(listOf(station))
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any()))
            .thenReturn(listOf(price("A", 1500)))
        // 경유 경로가 기본 경로보다 짧음 (Kakao API 오차)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters - 100))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        result[0].distance shouldBeExactly 0.0
        result[0].isActualDetour shouldBe true
    }

    // ─── 경로 조회 실패 ──────────────────────────────────────────────────────

    @Test
    fun `기본 경로 조회 실패 시 예외를 던진다`() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(null)

        assertThrows<IllegalStateException> {
            recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)
        }
    }

    @Test
    fun `polyline이 1점인 비정상 경로는 도메인 예외를 던진다`() {
        val singlePointRoute = Route(polyline = listOf(wgs84(37.0, 127.0)), distanceMeters = 1)
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(singlePointRoute)

        assertThrows<IllegalStateException> {
            recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)
        }
    }

    @Test
    fun `경유 경로 조회 실패 시 예외를 던진다`() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        val station = gasStation("A", lat = 37.05, lon = 127.005)

        whenever(gasStationRepository.findInBounds(any())).thenReturn(listOf(station))
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any()))
            .thenReturn(listOf(price("A", 1500)))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any())).thenReturn(null)

        assertThrows<IllegalStateException> {
            recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)
        }
    }

    // ─── 후보 없음 ───────────────────────────────────────────────────────────

    @Test
    fun `corridor 내 주유소가 없으면 빈 리스트를 반환한다`() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        whenever(gasStationRepository.findInBounds(any())).thenReturn(emptyList())
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any())).thenReturn(emptyList())

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        result shouldHaveSize 0
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun wgs84(lat: Double, lon: Double): Coordinate =
        Coordinate.fromWgs84(Coordinate.Wgs84(lat, lon))

    private fun gasStation(id: String, lat: Double, lon: Double) =
        GasStation(id = id, name = id, brand = "SKE", latitude = lat, longitude = lon)

    private fun price(stationId: String, price: Int) =
        GasStationPrice(
            id = GasStationPriceId(stationId = stationId, fuelType = FuelType.GASOLINE),
            station = gasStation(stationId, 37.05, 127.005),
            price = price,
            updatedAt = LocalDate.now()
        )
}
