package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.gasstation.client.OpinetClient
import com.kumoh.lbs.gasstation.client.OpinetClient.SortType
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.NearbyPricedGasStation
import com.kumoh.lbs.gasstation.domain.PricedGasStation
import com.kumoh.lbs.infra.ExternalApiException
import org.junit.jupiter.api.Test
import com.kumoh.lbs.TestcontainersConfiguration
import com.kumoh.lbs.auth.User
import com.kumoh.lbs.auth.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * 반경 추천 E2E. 추천 API는 인증 필수이며 유종·연비는 로그인 사용자 프로필에서 읽으므로,
 * jwt() 인증 + 차량 프로필(GASOLINE, 연비 10.0)이 입력된 User를 시드한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Transactional
class GasStationE2eTest(
    val mockMvc: MockMvc,
    @MockitoBean val opinetClient: OpinetClient,
    val userRepository: UserRepository
) {

    private val cheapStation = nearby("ST001", "싼주유소", brand = "SKE", price = 1600, distanceMeters = 1000.0)
    private val closeStation = nearby("ST002", "가까운주유소", brand = "GSC", price = 1800, distanceMeters = 300.0)
    private val expensiveStation = nearby("ST003", "비싼주유소", brand = "HDO", price = 2000, distanceMeters = 2000.0)

    @BeforeEach
    fun setUp() {
        userRepository.save(User(kakaoSub = KAKAO_SUB, name = "테스터", fuelType = FuelType.GASOLINE, fuelEfficiency = 10.0))
    }

    private fun nearby(id: String, name: String, brand: String, price: Int, distanceMeters: Double) =
        NearbyPricedGasStation(
            priced = PricedGasStation(
                station = GasStation(id = id, name = name, brand = brand, latitude = 38.0, longitude = 128.0),
                price = price
            ),
            distanceMeters = distanceMeters
        )

    @Test
    fun `WGS84 좌표로 주유소를 추천하면 점수순으로 정렬된 결과를 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), eq(5000), eq(FuelType.GASOLINE), eq(SortType.PRICE)))
            .thenReturn(listOf(cheapStation, closeStation, expensiveStation))

        mockMvc.get("/api/gas-stations/recommendations/radius") {
            with(jwt().jwt { it.subject(KAKAO_SUB) })
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].opinetStationId") { value("ST001") }
            jsonPath("$[0].name") { value("싼주유소") }
            jsonPath("$[0].score") { value(64160.0) }
            jsonPath("$[1].opinetStationId") { value("ST002") }
            jsonPath("$[1].score") { value(72054.0) }
            jsonPath("$[2].opinetStationId") { value("ST003") }
            jsonPath("$[2].score") { value(80400.0) }
        }
    }

    @Test
    fun `limit보다 주유소가 적으면 있는 만큼만 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), any(), any(), any()))
            .thenReturn(listOf(cheapStation))

        mockMvc.get("/api/gas-stations/recommendations/radius") {
            with(jwt().jwt { it.subject(KAKAO_SUB) })
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `OPINET 호출이 ExternalApiException을 던지면 503과 에러 detail을 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), any(), any(), any()))
            .thenThrow(ExternalApiException("주유소 데이터를 불러올 수 없습니다."))

        mockMvc.get("/api/gas-stations/recommendations/radius") {
            with(jwt().jwt { it.subject(KAKAO_SUB) })
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.detail") { value("주유소 데이터를 불러올 수 없습니다.") }
        }
    }

    @Test
    fun `주변에 주유소가 없으면 빈 배열을 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), any(), any(), any()))
            .thenReturn(emptyList())

        mockMvc.get("/api/gas-stations/recommendations/radius") {
            with(jwt().jwt { it.subject(KAKAO_SUB) })
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `차량 프로필이 없는 사용자는 기본값(휘발유)으로 추천받는다`() {
        userRepository.save(User(kakaoSub = "no-profile-sub"))
        // 프로필이 없으면 유종은 휘발유로 기본 조회된다. 이 스텁이 매칭돼 결과가 나오면 기본 유종이 GASOLINE이라는 증거.
        whenever(opinetClient.searchByRadius(any(), eq(5000), eq(FuelType.GASOLINE), eq(SortType.PRICE)))
            .thenReturn(listOf(cheapStation, closeStation, expensiveStation))

        mockMvc.get("/api/gas-stations/recommendations/radius") {
            with(jwt().jwt { it.subject("no-profile-sub") })
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
        }
    }

    @Test
    fun `로그인하지 않으면 401을 반환한다`() {
        mockMvc.get("/api/gas-stations/recommendations/radius") {
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("refuelLiters", "40.0")
            param("limit", "3")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    companion object {
        private const val KAKAO_SUB = "radius-e2e-user"
    }
}
