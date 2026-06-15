package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.TestcontainersConfiguration
import com.kumoh.lbs.auth.User
import com.kumoh.lbs.auth.UserRepository
import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.GasStationPrice
import com.kumoh.lbs.gasstation.domain.GasStationPriceId
import com.kumoh.lbs.gasstation.domain.Route
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import com.kumoh.lbs.geo.Coordinate
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * 10명 동시 요청 부하 테스트.
 *
 * 논문 발표용 데모의 최악 순간(10명이 동시에 버튼 클릭)에 대해,
 * routeExecutor·커넥션 풀·파동 병렬이 병목 없이 동시 처리되는지 실측 검증.
 *
 * - Kakao는 @MockitoBean으로 스텁하되 Thread.sleep(100ms)로 실제 RTT를 시뮬레이션.
 * - 병렬 dispatch가 실제로 겹쳐 실행되는지 동시 호출 피크를 AtomicInteger로 관측.
 * - 모든 요청이 200 OK, 타임아웃/큐잉 지연 없이 허용 시간 내 완료되는지 확인.
 * - 추천 API는 인증 필수이므로 jwt() 인증 + 차량 프로필(GASOLINE, 연비 10.0) User를 시드한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ConcurrentUserLoadTest(
    val mockMvc: MockMvc,
    @MockitoBean val kakaoDirectionsClient: KakaoDirectionsClient,
    @Autowired val gasStationRepository: GasStationRepository,
    @Autowired val gasStationPriceRepository: GasStationPriceRepository,
    @Autowired val userRepository: UserRepository
) {

    companion object {
        private const val SIMULATED_RTT_MS = 100L
        private const val CONCURRENT_USERS = 10
        private const val KAKAO_SUB = "load-test-user"
    }

    private val polyline = listOf(
        Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0)),
        Coordinate.fromWgs84(Coordinate.Wgs84(37.05, 127.05)),
        Coordinate.fromWgs84(Coordinate.Wgs84(37.1, 127.1))
    )
    private val baseRoute = Route(polyline = polyline, distanceMeters = 15_000)

    private val inFlight = AtomicInteger(0)
    private val maxInFlight = AtomicInteger(0)

    @BeforeEach
    fun setUp() {
        inFlight.set(0)
        maxInFlight.set(0)

        userRepository.save(User(kakaoSub = KAKAO_SUB, name = "테스터", fuelType = FuelType.GASOLINE, fuelEfficiency = 10.0))

        // 주유소 5건 + 가격 시드 — pruning 여지가 있도록 가격 차이 유지
        val stations = listOf(
            GasStation(id = "L01", name = "주유소1", brand = "SKE", latitude = 37.05, longitude = 127.045),
            GasStation(id = "L02", name = "주유소2", brand = "SKE", latitude = 37.05, longitude = 127.050),
            GasStation(id = "L03", name = "주유소3", brand = "SKE", latitude = 37.06, longitude = 127.055),
            GasStation(id = "L04", name = "주유소4", brand = "SKE", latitude = 37.04, longitude = 127.048),
            GasStation(id = "L05", name = "주유소5", brand = "SKE", latitude = 37.05, longitude = 127.060)
        )
        gasStationRepository.saveAll(stations)
        gasStationPriceRepository.saveAll(
            listOf(
                priceOf("L01", 1500), priceOf("L02", 1550),
                priceOf("L03", 1600), priceOf("L04", 1650), priceOf("L05", 1700)
            )
        )

        // base route + waypoint route 둘 다 동일 sleep으로 RTT 시뮬레이션.
        // inFlight 카운터로 '동시에 몇 건이 나가고 있었는지' 피크 관측.
        whenever(kakaoDirectionsClient.searchRoute(any(), any())).thenAnswer {
            observe {
                Thread.sleep(SIMULATED_RTT_MS)
                baseRoute
            }
        }
        whenever(kakaoDirectionsClient.searchRouteViaWaypoint(any(), any(), any())).thenAnswer {
            observe {
                Thread.sleep(SIMULATED_RTT_MS)
                Route(polyline = polyline, distanceMeters = baseRoute.distanceMeters + 300)
            }
        }
    }

    @AfterEach
    fun tearDown() {
        gasStationPriceRepository.deleteAll()
        gasStationRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `10명 동시 요청을 타임아웃이나 실패 없이 처리한다`() {
        val userExecutor = Executors.newFixedThreadPool(CONCURRENT_USERS)

        val elapsed = measureTimeMillis {
            val futures = (1..CONCURRENT_USERS).map {
                CompletableFuture.supplyAsync({ callEndpoint() }, userExecutor)
            }
            CompletableFuture.allOf(*futures.toTypedArray()).get(30, TimeUnit.SECONDS)
            val statuses = futures.map { it.join() }
            // 10명 전원 200 OK
            statuses.all { it == 200 } shouldBe true
        }

        userExecutor.shutdown()

        println("\n===== 10명 동시 부하 실측 =====")
        println("전체 소요: ${elapsed}ms")
        println("관측된 최대 동시 Kakao in-flight: ${maxInFlight.get()}")
        println("  ↳ 10명 × 파동 크기 3 = 이론 피크 30")
        println("=================================\n")

        // 병렬 처리 증거: 실제로 동시에 10건 이상이 in-flight였어야 한다 (큐잉이 아님)
        check(maxInFlight.get() >= CONCURRENT_USERS) {
            "동시 in-flight가 사용자 수 미만 = 큐잉 발생. 관측=${maxInFlight.get()}"
        }

        // routeExecutor가 30개까지 열려 있으므로, 10명 × 3 파동 병렬이면 첫 파동은 30 동시.
        // 순차 처리 최악(10 × 300ms ≈ 3초)보다 확실히 짧아야 병렬이 먹히는 것.
        elapsed shouldBeLessThan 2_500L
    }

    @Test
    fun `10명 연속 요청에서도 누적 실패가 0건이다`() {
        val rounds = 3
        val userExecutor = Executors.newFixedThreadPool(CONCURRENT_USERS)
        val failures = AtomicInteger(0)

        repeat(rounds) { round ->
            val futures = (1..CONCURRENT_USERS).map {
                CompletableFuture.supplyAsync({ callEndpoint() }, userExecutor)
            }
            CompletableFuture.allOf(*futures.toTypedArray()).get(30, TimeUnit.SECONDS)
            futures.forEach { if (it.join() != 200) failures.incrementAndGet() }
            println("라운드 ${round + 1}/$rounds 완료 — 누적 실패=${failures.get()}")
        }

        userExecutor.shutdown()
        failures.get() shouldBe 0
    }

    private fun callEndpoint(): Int =
        mockMvc.get("/api/gas-stations/recommendations/route") {
            with(jwt().jwt { it.subject(KAKAO_SUB) })
            param("originLatitude", "37.0")
            param("originLongitude", "127.0")
            param("destinationLatitude", "37.1")
            param("destinationLongitude", "127.1")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andReturn().response.status

    private fun <T> observe(block: () -> T): T {
        val now = inFlight.incrementAndGet()
        maxInFlight.updateAndGet { prev -> maxOf(prev, now) }
        try {
            return block()
        } finally {
            inFlight.decrementAndGet()
        }
    }

    private fun priceOf(id: String, price: Int): GasStationPrice =
        GasStationPrice(
            id = GasStationPriceId(stationId = id, fuelType = FuelType.GASOLINE),
            station = gasStationRepository.findById(id).orElseThrow(),
            price = price,
            updatedAt = LocalDate.now()
        )
}
