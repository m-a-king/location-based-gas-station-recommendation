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

    // ─── POLYLINE_MBR 단계 ───────────────────────────────────────────────────

    @Test
    fun `후보가 적으면 POLYLINE_MBR 단계에서 MBR 버퍼 내 주유소 모두 통과한다`() {
        // 새 cascade: 후보 ≤ 30이면 TIGHT_CORRIDOR 필터 발동 안 함 → MBR 버퍼(5km) 안 주유소는 모두 유지
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        val farStation  = gasStation("FAR",  lat = 37.05, lon = 127.025) // polyline 기준 약 2.2km
        val nearStation = gasStation("NEAR", lat = 37.05, lon = 127.015)

        whenever(gasStationRepository.findInBounds(any())).thenReturn(listOf(farStation, nearStation))
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any()))
            .thenReturn(listOf(price("FAR", 1500), price("NEAR", 1500)))
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = 11500))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 5)

        result shouldHaveSize 2
        result.map { it.station.id }.toSet() shouldBe setOf("FAR", "NEAR")
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

    // ─── price lower bound pruning ──────────────────────────────────────────

    @Test
    fun `현재 최선 score보다 lower bound가 큰 주유소는 Kakao API를 호출하지 않는다`() {
        // refuelLiters=40, fuelEfficiency=10
        // A(1400원): score = 1400×40 + 0 = 56000  → bestScore = 56000
        // B(1500원): lower bound = 1500×40 = 60000 > 56000 → pruning
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        val stationA = gasStation("A", lat = 37.05, lon = 127.005)
        val stationB = gasStation("B", lat = 37.05, lon = 127.005)

        whenever(gasStationRepository.findInBounds(any())).thenReturn(listOf(stationA, stationB))
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any()))
            .thenReturn(listOf(price("A", 1400), price("B", 1500)))
        // A 경유 시 우회 없음 (기본 경로와 동일) → actualDetour = 0 → score = 56000
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        // A만 호출되고 B는 pruning
        verify(kakaoDirectionsClient, times(1)).searchRouteViaWaypoint(any(), any(), any())
    }

    @Test
    fun `가격이 비슷해 pruning이 안 되면 모든 후보에 Kakao API를 호출한다`() {
        // refuelLiters=40
        // A(1500원): score = 1500×40 + (1000/1000/10)×1500 = 60000 + 150 = 60150
        // B(1501원): lower bound = 1501×40 = 60040 < 60150 → pruning 불가, Kakao 호출
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        val stationA = gasStation("A", lat = 37.05, lon = 127.005)
        val stationB = gasStation("B", lat = 37.05, lon = 127.005)

        whenever(gasStationRepository.findInBounds(any())).thenReturn(listOf(stationA, stationB))
        whenever(gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(any(), any()))
            .thenReturn(listOf(price("A", 1500), price("B", 1501)))
        // 우회 1000m
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 1000))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        verify(kakaoDirectionsClient, times(2)).searchRouteViaWaypoint(any(), any(), any())
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

        result[0].detourDistanceMeters shouldBeExactly 0.0
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
