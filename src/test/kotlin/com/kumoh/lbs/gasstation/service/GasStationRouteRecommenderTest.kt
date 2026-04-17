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
import kotlin.math.abs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate

/**
 * 경로 기반 추천 통합 테스트.
 *
 * DB는 Testcontainers로 실제 MySQL을 띄우고, Kakao Directions만 외부 호출이므로 MockitoBean으로 스텁한다.
 * 모킹 원칙: 외부 HTTP 호출(KakaoDirectionsClient)만 모킹. repository·JPA·서비스 로직은 실제 구동.
 *
 * E2E(RouteGasStationE2eTest)와 역할 분리:
 * - 여기: pruning 발동 여부 검증(Kakao 호출 수), 도메인 방어 예외, 서비스 반환값 세부 필드
 * - E2E: HTTP 레이어 + 응답 JSON + 컨트롤러 위임까지
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

    // ─── 가격 없는 주유소 제외 ───────────────────────────────────────────────

    @Test
    fun `가격 정보가 없는 주유소는 후보에서 조용히 제외된다`() {
        saveStation("A", lat = 37.05, lon = 127.005)
        saveStation("B", lat = 37.05, lon = 127.005)
        savePrice("A", 1500)  // B는 가격 미등록
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = 11500))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 5)

        result.scored shouldHaveSize 1
        result.scored[0].station.id shouldBe "A"
    }

    // ─── price lower bound pruning ──────────────────────────────────────────

    @Test
    fun `현재 최선 score보다 lower bound가 큰 주유소는 Kakao API를 호출하지 않는다`() {
        // refuelLiters=40, fuelEfficiency=10
        // A(1400원): 우회 0m → score = 1400×40 = 56000 → bestScore = 56000
        // B(1500원): lower bound = 1500×40 = 60000 > 56000 → pruning
        saveStation("A", lat = 37.05, lon = 127.005)
        saveStation("B", lat = 37.05, lon = 127.005)
        savePrice("A", 1400)
        savePrice("B", 1500)
        // A 경유 시 기본 경로와 동일(우회 0)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        verify(kakaoDirectionsClient, times(1)).searchRouteViaWaypoint(any(), any(), any())
    }

    @Test
    fun `가격이 비슷해 pruning이 안 되면 모든 후보에 Kakao API를 호출한다`() {
        // A(1500원): 우회 1000m → score = 60000 + 150 = 60150
        // B(1501원): lower bound = 60040 < 60150 → pruning 불가, Kakao 호출
        saveStation("A", lat = 37.05, lon = 127.005)
        saveStation("B", lat = 37.05, lon = 127.005)
        savePrice("A", 1500)
        savePrice("B", 1501)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 1000))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        verify(kakaoDirectionsClient, times(2)).searchRouteViaWaypoint(any(), any(), any())
    }

    // ─── 우회 거리 음수 보정 ─────────────────────────────────────────────────

    @Test
    fun `경유 경로가 기본 경로보다 짧게 나오면 우회거리는 0으로 보정된다`() {
        saveStation("A", lat = 37.05, lon = 127.005)
        savePrice("A", 1500)
        // 경유 경로가 기본 경로보다 짧음 (Kakao 실제 오차)
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters - 100))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 1)

        result.scored[0].detourDistanceMeters shouldBeExactly 0.0
        result.scored[0].isActualDetour shouldBe true
    }

    // ─── cascade 단계 전이 ───────────────────────────────────────────────────

    @Test
    fun `후보가 N_THRESHOLD 30건을 초과하면 TIGHT_CORRIDOR 단계로 좁혀 corridor 밖 주유소를 제외한다`() {
        // polyline: (37.0, 127.0) → (37.1, 127.0). 북쪽 직선.
        // corridor 내 (polyline distance ≈ 450m) 주유소 30건, 가격 1600
        repeat(30) { i ->
            saveStation("IN_$i", lat = 37.05, lon = 127.005)
            savePrice("IN_$i", 1600)
        }
        // corridor 밖 (polyline distance ≈ 3.3km, TIGHT_CORRIDOR 2000m 초과) 주유소 1건, 최저가 1500
        // MBR buffer(≈6.6km) 안쪽이므로 1단계 수집은 통과하고 2단계에서 잘려야 한다
        saveStation("OUT_1", lat = 37.05, lon = 127.04)
        savePrice("OUT_1", 1500)

        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = 11200))

        val result = recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        // TIGHT_CORRIDOR 단계에서 OUT_1 제외 → 결과에 없음
        result.scored.map { it.station.id } shouldNotContain "OUT_1"
        // corridor 밖 좌표로는 Kakao 경유 경로 호출 자체가 없어야 한다
        verify(kakaoDirectionsClient, never()).searchRouteViaWaypoint(
            any(), any(),
            argThat { abs(wgs84.longitude - 127.04) < 0.001 && abs(wgs84.latitude - 37.05) < 0.001 }
        )
    }

    @Test
    fun `후보가 HARD_CAP 30건을 초과하면 PRICE_CAPPED 단계에서 저가 상위 30건만 Kakao 호출된다`() {
        // corridor 내 31건, 동일 가격 1500 → price lower bound pruning 무력화
        // (동가면 kthBestScore ≥ 후보 lower bound가 성립 못 해 pruning 조건 미성립)
        // 이 상태에서 HARD_CAP(30)가 적용되면 31 → 30건으로 잘려 Kakao 호출이 30회가 된다
        repeat(31) { i ->
            saveStation("P_$i", lat = 37.05, lon = 127.005)
            savePrice("P_$i", 1500)
        }
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any()))
            .thenReturn(Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 100))

        recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)

        verify(kakaoDirectionsClient, times(30)).searchRouteViaWaypoint(any(), any(), any())
    }

    // ─── 도메인 방어 ─────────────────────────────────────────────────────────

    @Test
    fun `polyline이 1점인 비정상 경로는 도메인 예외를 던진다`() {
        val singlePointRoute = Route(polyline = listOf(wgs84(37.0, 127.0)), distanceMeters = 1)
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenReturn(singlePointRoute)

        assertThrows<IllegalStateException> {
            recommender.recommend(origin, destination, FuelType.GASOLINE, 40.0, 10.0, limit = 3)
        }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun wgs84(lat: Double, lon: Double): Coordinate =
        Coordinate.fromWgs84(Coordinate.Wgs84(lat, lon))

    private fun saveStation(id: String, lat: Double, lon: Double) {
        gasStationRepository.save(
            GasStation(id = id, name = id, brand = "SKE", latitude = lat, longitude = lon)
        )
    }

    private fun savePrice(stationId: String, price: Int) {
        val station = gasStationRepository.findById(stationId).orElseThrow()
        gasStationPriceRepository.save(
            GasStationPrice(
                id = GasStationPriceId(stationId = stationId, fuelType = FuelType.GASOLINE),
                station = station,
                price = price,
                updatedAt = LocalDate.now()
            )
        )
    }
}
