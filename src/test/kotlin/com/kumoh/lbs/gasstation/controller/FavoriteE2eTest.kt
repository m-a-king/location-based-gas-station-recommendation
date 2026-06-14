package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.TestcontainersConfiguration
import com.kumoh.lbs.auth.User
import com.kumoh.lbs.auth.UserRepository
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.GasStationPrice
import com.kumoh.lbs.gasstation.domain.GasStationPriceId
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 즐겨찾기 추가·삭제·조회 E2E. 인증은 spring-security-test의 jwt() post-processor로
 * 카카오 sub가 담긴 Bearer principal을 주입해 시뮬레이션한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Transactional
class FavoriteE2eTest(
    val mockMvc: MockMvc,
    val userRepository: UserRepository,
    val gasStationRepository: GasStationRepository,
    val gasStationPriceRepository: GasStationPriceRepository
) {

    private val kakaoSub = "kakao-favorite-test"

    @BeforeEach
    fun setUp() {
        userRepository.save(User(kakaoSub = kakaoSub, name = "테스터", fuelType = FuelType.GASOLINE))
        val station = gasStationRepository.save(
            GasStation(
                id = STATION_ID,
                name = "즐겨찾기주유소",
                brand = "SKE",
                address = "서울시 어딘가",
                latitude = 37.5,
                longitude = 127.0
            )
        )
        gasStationPriceRepository.save(
            GasStationPrice(
                id = GasStationPriceId(STATION_ID, FuelType.GASOLINE),
                station = station,
                price = 1650,
                updatedAt = LocalDate.now()
            )
        )
    }

    @Test
    fun `즐겨찾기를 추가하면 목록에 가격과 함께 나타난다`() {
        mockMvc.post("/users/me/favorites/$STATION_ID") {
            with(jwt().jwt { it.subject(kakaoSub) })
        }.andExpect { status { isCreated() } }

        mockMvc.get("/users/me/favorites") {
            with(jwt().jwt { it.subject(kakaoSub) })
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].opinetStationId") { value(STATION_ID) }
            jsonPath("$[0].name") { value("즐겨찾기주유소") }
            jsonPath("$[0].fuelType") { value("GASOLINE") }
            jsonPath("$[0].price") { value(1650) }
        }
    }

    @Test
    fun `같은 주유소를 두 번 추가해도 목록에는 하나만 남는다`() {
        repeat(2) {
            mockMvc.post("/users/me/favorites/$STATION_ID") {
                with(jwt().jwt { it.subject(kakaoSub) })
            }.andExpect { status { isCreated() } }
        }

        mockMvc.get("/users/me/favorites") {
            with(jwt().jwt { it.subject(kakaoSub) })
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `즐겨찾기를 삭제하면 목록에서 사라진다`() {
        mockMvc.post("/users/me/favorites/$STATION_ID") {
            with(jwt().jwt { it.subject(kakaoSub) })
        }.andExpect { status { isCreated() } }

        mockMvc.delete("/users/me/favorites/$STATION_ID") {
            with(jwt().jwt { it.subject(kakaoSub) })
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/users/me/favorites") {
            with(jwt().jwt { it.subject(kakaoSub) })
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `존재하지 않는 주유소를 즐겨찾기하면 404를 반환한다`() {
        mockMvc.post("/users/me/favorites/NO-SUCH-STATION") {
            with(jwt().jwt { it.subject(kakaoSub) })
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { exists() }
        }
    }

    // ─── 인증 보호: 로그인 토큰 없이는 어떤 동작도 불가 ──────────────────────────
    // 우리 서비스는 자체 토큰을 발급하지 않고 카카오 OIDC 토큰을 Bearer로 검증한다.
    // oauth2Login(302)과 resourceServer(401)가 함께 설정돼 있으나, 미인증 API 요청은
    // 리다이렉트가 아니라 401 + WWW-Authenticate: Bearer로 차단됨을 고정한다(SPA에 적합).

    @Test
    fun `로그인 토큰 없이 즐겨찾기 목록을 조회하면 401을 반환한다`() {
        mockMvc.get("/users/me/favorites")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그인 토큰 없이 즐겨찾기를 추가하면 401을 반환한다`() {
        mockMvc.post("/users/me/favorites/$STATION_ID")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그인 토큰 없이 즐겨찾기를 삭제하면 401을 반환한다`() {
        mockMvc.delete("/users/me/favorites/$STATION_ID")
            .andExpect { status { isUnauthorized() } }
    }

    companion object {
        private const val STATION_ID = "ST-FAV-1"
    }
}
