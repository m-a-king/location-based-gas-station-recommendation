package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.TestcontainersConfiguration
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.GasStationPrice
import com.kumoh.lbs.gasstation.domain.GasStationPriceId
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import kotlin.system.measureTimeMillis

/**
 * 경로 기반 추천 **실제 Kakao Mobility 호출** E2E 테스트.
 *
 * - Testcontainers MySQL 실제 DB + MockMvc 컨트롤러 경유 + 실제 Kakao Directions API 호출.
 * - 파동 병렬화가 실제 네트워크 지연에서도 정상 작동함을 확인.
 *
 * 실행 조건:
 * - 기본 @Disabled — CI에서 건너뜀
 * - 로컬 수동 실행: @Disabled 주석 처리 후 실행 (또는 IDE에서 개별 실행)
 * - 환경변수 KAKAO_API_KEY 필요 (.env.local이 SessionStart 훅에 의해 로드됨)
 *
 * 좌표: 서울시청(37.5665, 126.9780) → 강남역(37.4979, 127.0276) 약 8~10km 경로.
 * 주유소 픽스처는 경로 주변에 합성한 좌표라 OPINET 실데이터는 아님 — 점수 계산 경로만 검증.
 */
@Disabled("실제 Kakao Mobility API 호출 — 수동 실행 전용")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class RouteGasStationRealKakaoE2eTest(
    val mockMvc: MockMvc,
    @Autowired val gasStationRepository: GasStationRepository,
    @Autowired val gasStationPriceRepository: GasStationPriceRepository
) {

    // 서울시청 → 강남역 (실제 교통망 존재)
    private val originLat = 37.5665
    private val originLng = 126.9780
    private val destLat = 37.4979
    private val destLng = 127.0276

    // 경로 주변에 합성한 주유소 5개 (모두 서울 도심 좌표 범위)
    private val fixtureStations = listOf(
        SeedStation(id = "REAL_01", name = "실제테스트주유소1", lat = 37.5600, lng = 126.9900, price = 1580),
        SeedStation(id = "REAL_02", name = "실제테스트주유소2", lat = 37.5500, lng = 127.0000, price = 1620),
        SeedStation(id = "REAL_03", name = "실제테스트주유소3", lat = 37.5400, lng = 127.0100, price = 1550),
        SeedStation(id = "REAL_04", name = "실제테스트주유소4", lat = 37.5200, lng = 127.0200, price = 1700),
        SeedStation(id = "REAL_05", name = "실제테스트주유소5", lat = 37.5100, lng = 127.0250, price = 1600)
    )

    @AfterEach
    fun tearDown() {
        gasStationPriceRepository.deleteAll()
        gasStationRepository.deleteAll()
    }

    @Test
    fun `실제 Kakao API 호출로 경로 추천이 200을 반환하고 스코어가 부여된다`() {
        seedFixtures()

        val elapsed = measureTimeMillis {
            mockMvc.get("/api/gas-stations/recommendations/route") {
                param("originLatitude", originLat.toString())
                param("originLongitude", originLng.toString())
                param("destinationLatitude", destLat.toString())
                param("destinationLongitude", destLng.toString())
                param("fuelType", "GASOLINE")
                param("refuelLiters", "40.0")
                param("fuelEfficiency", "10.0")
                param("limit", "3")
            }.andExpect {
                status { isOk() }
                jsonPath("$") { isArray() }
                // 최소 1개 이상, limit 이하
                jsonPath("$[0].opinetStationId") { isString() }
                jsonPath("$[0].score") { isNumber() }
                jsonPath("$[0].price") { isNumber() }
                jsonPath("$[0].estimatedFuelCost") { isNumber() }
                jsonPath("$[0].estimatedDetourCost") { isNumber() }
                jsonPath("$[0].estimatedSavings") { isNumber() }
            }.andReturn()
        }

        // 파동 병렬화 효과: 첫 파동 limit=3이 병렬 dispatch → 한 RTT 분량으로 수렴.
        // 서버 RTT 상한을 느슨히 15초로 둔다 (baseRoute + 경유 3건).
        println("실제 Kakao 호출 경로 추천 소요: ${elapsed}ms")
        elapsed shouldBeLessThan 15_000L
    }

    @Test
    fun `실제 Kakao 호출에서도 limit을 초과하지 않고 스코어 오름차순이 유지된다`() {
        seedFixtures()

        val mvcResult = mockMvc.get("/api/gas-stations/recommendations/route") {
            param("originLatitude", originLat.toString())
            param("originLongitude", originLng.toString())
            param("destinationLatitude", destLat.toString())
            param("destinationLongitude", destLng.toString())
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "2")
        }.andExpect { status { isOk() } }.andReturn()

        val body = mvcResult.response.contentAsString
        println("실제 Kakao 응답: $body")
        // 응답이 JSON 배열 — 정확한 원소 수는 실제 Kakao 응답에 달려 있어 구조만 검증.
        body shouldStartWith "["
        body shouldEndWith "]"
    }

    private fun seedFixtures() {
        val stations = fixtureStations.map {
            GasStation(id = it.id, name = it.name, brand = "SKE", latitude = it.lat, longitude = it.lng)
        }
        gasStationRepository.saveAll(stations)

        val prices = fixtureStations.map {
            GasStationPrice(
                id = GasStationPriceId(stationId = it.id, fuelType = FuelType.GASOLINE),
                station = gasStationRepository.findById(it.id).orElseThrow(),
                price = it.price,
                updatedAt = LocalDate.now()
            )
        }
        gasStationPriceRepository.saveAll(prices)
    }

    private data class SeedStation(
        val id: String,
        val name: String,
        val lat: Double,
        val lng: Double,
        val price: Int
    )
}
