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
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.system.measureTimeMillis

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
    private val nullSequenceCounter = AtomicInteger()

    @BeforeEach
    fun setUp() {
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(baseRoute)
        nullSequenceCounter.set(0)
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
        // 두 후보를 ON_ROUTE_RADIUS(500m) 밖에 두어 ROUTE_PRICE_CEILING fallback 경로로 진입.
        // 단계 3 pruning 검증 자체는 cap과 무관하므로 시나리오 본래 의도는 보존.
        saveStations("A" to (37.05 to 127.04), "B" to (37.05 to 127.04))
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
    fun `ROUTE_PRICE_CEILING은 후보 수와 무관하게 경로상 최저가보다 비싼 후보를 외부 호출 없이 배제한다`() {
        // 작은 N(=4): ON_*: 경로상(≈450m) 3건 1500원. OUT_HIGH: 경로상 외(≈3.6km) 1건 1600원.
        // p_route = 1500. 가격 > 1500인 OUT_HIGH는 식 (2) 하한에 의해 1위가 될 수 없으므로 cap에서 정확히 배제.
        // 후보 수 임계값(N>30 트리거)을 제거했으므로 작은 N에서도 cap이 발동해야 한다.
        saveStations(
            "ON_0" to (37.05 to 127.005),
            "ON_1" to (37.05 to 127.005),
            "ON_2" to (37.05 to 127.005),
            "OUT_HIGH" to (37.05 to 127.04)
        )
        savePrices("ON_0" to 1500, "ON_1" to 1500, "ON_2" to 1500, "OUT_HIGH" to 1600)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 100))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        result.scored.map { it.station.id } shouldNotContain "OUT_HIGH"
        verify(kakaoDirectionsClient, never()).searchRouteViaWaypoint(
            any(), any(),
            argThat { abs(wgs84.longitude - 127.04) < 0.001 && abs(wgs84.latitude - 37.05) < 0.001 }
        )
    }

    @Test
    fun `경로상 후보가 0개면 ROUTE_PRICE_CEILING은 fallback하고 후보가 그대로 단계 3에 진입한다`() {
        // 31건 모두 경로상 외(≈3.6km, ON_ROUTE_RADIUS 500m 초과). 경로상 후보 0개 → cap 미적용 fallback.
        // 동가 1500으로 pruning 무력화하여 PRICE_CAPPED HARD_CAP 30이 그대로 호출 30회를 만듦.
        saveStations(*(0 until 31).map { "OUT_$it" to (37.05 to 127.04) }.toTypedArray())
        savePrices(*(0 until 31).map { "OUT_$it" to 1500 }.toTypedArray())
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 100))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        verify(kakaoDirectionsClient, times(30)).searchRouteViaWaypoint(any(), any(), any())
    }

    @Test
    fun `cap 통과 후보가 limit보다 적으면 그 수만큼만 반환된다`() {
        // ON_LOW: 경로상 1400원(p_route). HIGH_*: 경로상 5건 1500~1504원(p_route 초과).
        // p_route = 1400. 가격 ≤ 1400은 ON_LOW 하나뿐 → 단계 3 호출도 1회, 결과도 1개.
        saveStations(
            *(listOf("ON_LOW" to (37.05 to 127.005)) +
                (0 until 5).map { "HIGH_$it" to (37.05 to 127.005) }).toTypedArray()
        )
        savePrices(
            *(listOf("ON_LOW" to 1400) +
                (0 until 5).map { "HIGH_$it" to (1500 + it) }).toTypedArray()
        )
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 100))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        result.scored shouldHaveSize 1
        result.scored[0].station.id shouldBe "ON_LOW"
        verify(kakaoDirectionsClient, times(1)).searchRouteViaWaypoint(any(), any(), any())
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
    fun `파동 내 Kakao 호출은 routeExecutor의 별도 스레드에서 병렬로 실행된다`() {
        // limit = 3이므로 첫 파동 = 3건 병렬 dispatch.
        saveStations("A" to (37.05 to 127.005), "B" to (37.05 to 127.005), "C" to (37.05 to 127.005))
        savePrices("A" to 1500, "B" to 1500, "C" to 1500)  // 동가 → pruning 불가, 3건 전부 호출

        val observedThreads = ConcurrentHashMap.newKeySet<String>()
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any())).thenAnswer {
            observedThreads += Thread.currentThread().name
            Thread.sleep(150)  // 순차면 총 450ms, 병렬이면 ~150ms
            Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 100)
        }

        val elapsed = measureTimeMillis {
            recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)
        }

        // 호출은 routeExecutor 스레드에서 발생. 이름 prefix가 "route-kakao-"임을 검증.
        observedThreads.size shouldBe 3
        observedThreads.forEach { it shouldStartWith "route-kakao-" }
        // 병렬성 증거: 3× Thread.sleep(150)가 순차면 ≥ 450ms. 병렬이면 250ms 내에 충분.
        elapsed shouldBeLessThan 400L
    }

    @Test
    fun `파동 내 응답 순서가 뒤섞여도 결과는 점수 오름차순으로 정렬된다`() {
        // A: 느린 응답 / B: 빠른 응답. B가 먼저 완료되어도 A가 더 저가라 1등이어야 함.
        // 두 후보 모두 경로상 외 좌표로 두어 ROUTE_PRICE_CEILING fallback. 파동 정렬 검증 본래 의도 보존.
        saveStations("A" to (37.05 to 127.04), "B" to (37.05 to 127.04))
        savePrices("A" to 1400, "B" to 1500)

        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any())).thenAnswer { invocation ->
            val waypoint = invocation.arguments[2] as Coordinate
            val isA = abs(waypoint.wgs84.latitude - 37.05) < 0.001
            if (isA) Thread.sleep(200)  // A만 일부러 느리게
            Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 50)
        }

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 2)

        result.scored shouldHaveSize 2
        // 가격 1400인 A가 score 기준 1등
        result.scored[0].station.id shouldBe "A"
        result.scored[1].station.id shouldBe "B"
    }

    @Test
    fun `파동 내 일부 호출이 null을 반환해도 나머지는 정상 채점된다`() {
        saveStations("A" to (37.05 to 127.005), "B" to (37.05 to 127.005), "C" to (37.05 to 127.005))
        savePrices("A" to 1500, "B" to 1500, "C" to 1500)

        // B만 실패(null), A·C는 정상.
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any())).thenAnswer { invocation ->
            val waypoint = invocation.arguments[2] as Coordinate
            // 동일 좌표라 구분 불가 — 대신 호출 순서로 2번째만 실패 처리
            val ordinal = nullSequenceCounter.incrementAndGet()
            if (ordinal == 2) null
            else Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 100)
        }

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        result.scored shouldHaveSize 2
        verify(kakaoDirectionsClient, times(3)).searchRouteViaWaypoint(any(), any(), any())
    }

    @Test
    fun `파동 경계에서 price lower bound pruning이 작동해 다음 파동은 dispatch되지 않는다`() {
        // 후보 4건: A 1000 / B 1000 / C 2000 / D 2000. limit = 2.
        // 첫 파동 = 2: [A(1000), B(1000)] 호출 → 점수 ≈ 40000. kthBestScore = 40000.
        // 두 번째 파동 경계: candidates[2] = C(2000). 2000 × 40 = 80000 > 40000 → pruning break.
        // 결과: Kakao 호출은 정확히 2회.
        saveStations(
            "A" to (37.05 to 127.005),
            "B" to (37.05 to 127.005),
            "C" to (37.05 to 127.005),
            "D" to (37.05 to 127.005)
        )
        savePrices("A" to 1000, "B" to 1000, "C" to 2000, "D" to 2000)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 2)

        verify(kakaoDirectionsClient, times(2)).searchRouteViaWaypoint(any(), any(), any())
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
