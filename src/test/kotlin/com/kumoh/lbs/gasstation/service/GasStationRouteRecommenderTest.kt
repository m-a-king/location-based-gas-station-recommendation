package com.kumoh.lbs.gasstation.service

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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import kotlin.math.abs

/**
 * 경로 기반 추천 통합 테스트. DB는 Testcontainers, Kakao Directions만 MockitoBean.
 *
 * E2E(RouteGasStationE2eTest)와 역할 분리: 여기는 pruning 발동·도메인 방어·서비스 반환값 세부 필드.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class GasStationRouteRecommenderTest(
    @Autowired val recommender: GasStationRouteRecommender,
    @Autowired val gasStationRepository: GasStationRepository,
    @Autowired val gasStationPriceRepository: GasStationPriceRepository,
    @MockitoBean val kakaoDirectionsClient: KakaoDirectionsClient
) {

    private val polyline = listOf(
        wgs84(37.0, 127.0),
        wgs84(37.1, 127.0)
    )
    private val baseRoute = Route(polyline = polyline, distanceMeters = 11132)
    private val origin = wgs84(37.0, 127.0)
    private val destination = wgs84(37.1, 127.0)

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
    fun `가격 정보가 없는 주유소는 후보에서 조용히 제외된다`() {
        saveStations("A" to (37.05 to 127.005), "B" to (37.05 to 127.005))
        savePrices("A" to 1500)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = 11500))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 5)

        result.scored shouldHaveSize 1
        result.scored[0].station.id shouldBe "A"
    }

    @Test
    fun `현재 최선 score보다 lower bound가 큰 주유소는 Kakao API를 호출하지 않는다`() {
        saveStations("A" to (37.05 to 127.005), "B" to (37.05 to 127.005))
        savePrices("A" to 1400, "B" to 1500)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        verify(kakaoDirectionsClient, times(1)).searchRouteViaWaypoint(any(), any(), any())
    }

    @Test
    fun `가격이 비슷해 pruning이 안 되면 모든 후보에 Kakao API를 호출한다`() {
        saveStations("A" to (37.05 to 127.005), "B" to (37.05 to 127.005))
        savePrices("A" to 1500, "B" to 1501)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 1000))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        verify(kakaoDirectionsClient, times(2)).searchRouteViaWaypoint(any(), any(), any())
    }

    @Test
    fun `경유 경로가 기본 경로보다 짧게 나오면 우회거리는 0으로 보정된다`() {
        saveStations("A" to (37.05 to 127.005))
        savePrices("A" to 1500)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters - 100))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        result.scored[0].detourDistanceMeters shouldBeExactly 0.0
        result.scored[0].isActualDetour shouldBe true
    }

    @Test
    fun `후보가 N_THRESHOLD 30건을 초과하면 TIGHT_CORRIDOR 단계로 좁혀 corridor 밖 주유소를 제외한다`() {
        // corridor 내 30건(polyline ≈ 450m) + corridor 밖 1건(≈ 3.3km, TIGHT_CORRIDOR 2km 초과).
        // MBR buffer(≈ 6.6km)는 밖 1건까지 포함해야 2단계 필터 효과가 관찰된다
        saveStations(
            *((0 until 30).map { "IN_$it" to (37.05 to 127.005) } + ("OUT_1" to (37.05 to 127.04)))
                .toTypedArray()
        )
        savePrices(
            *((0 until 30).map { "IN_$it" to 1600 } + ("OUT_1" to 1500)).toTypedArray()
        )

        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = 11200))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        result.scored.map { it.station.id } shouldNotContain "OUT_1"
        verify(kakaoDirectionsClient, never()).searchRouteViaWaypoint(
            any(), any(),
            argThat { abs(wgs84.longitude - 127.04) < 0.001 && abs(wgs84.latitude - 37.05) < 0.001 }
        )
    }

    @Test
    fun `후보가 HARD_CAP 30건을 초과하면 PRICE_CAPPED 단계에서 저가 상위 30건만 Kakao 호출된다`() {
        // 동가로 price lower bound pruning(price × liters > kthBestScore)을 무력화해
        // HARD_CAP 잘림만 호출 수 차이를 만들게 한다
        saveStations(*(0 until 31).map { "P_$it" to (37.05 to 127.005) }.toTypedArray())
        savePrices(*(0 until 31).map { "P_$it" to 1500 }.toTypedArray())

        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 100))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        verify(kakaoDirectionsClient, times(30)).searchRouteViaWaypoint(any(), any(), any())
    }

    @Test
    fun `polyline이 1점인 비정상 경로는 도메인 예외를 던진다`() {
        val singlePointRoute = Route(polyline = listOf(wgs84(37.0, 127.0)), distanceMeters = 1)
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(singlePointRoute)

        assertThrows<IllegalStateException> {
            recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)
        }
    }

    private fun wgs84(lat: Double, lon: Double): Coordinate =
        Coordinate.fromWgs84(Coordinate.Wgs84(lat, lon))

    private fun saveStations(vararg defs: Pair<String, Pair<Double, Double>>) {
        gasStationRepository.saveAll(
            defs.map { (id, latLon) ->
                GasStation(id = id, name = id, brand = "SKE", latitude = latLon.first, longitude = latLon.second)
            }
        )
    }

    private fun savePrices(vararg entries: Pair<String, Int>) {
        val stations = gasStationRepository.findAllById(entries.map { it.first }).associateBy { it.id }
        gasStationPriceRepository.saveAll(
            entries.map { (id, price) ->
                GasStationPrice(
                    id = GasStationPriceId(stationId = id, fuelType = FuelType.GASOLINE),
                    station = stations.getValue(id),
                    price = price,
                    updatedAt = LocalDate.now()
                )
            }
        )
    }
}
