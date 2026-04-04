package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.gasstation.client.OpinetClient
import com.kumoh.lbs.gasstation.client.OpinetClient.SortType
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.NearbyStation
import org.junit.jupiter.api.Test
import com.kumoh.lbs.TestcontainersConfiguration
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class GasStationE2eTest(
    val mockMvc: MockMvc,
    @MockitoBean val opinetClient: OpinetClient
) {

    private val cheapStation = NearbyStation(
        station = GasStation(id = "ST001", name = "싼주유소", brand = "SKE", latitude = 38.0, longitude = 128.0),
        price = 1600, distanceMeters = 1000.0
    )

    private val closeStation = NearbyStation(
        station = GasStation(id = "ST002", name = "가까운주유소", brand = "GSC", latitude = 38.0, longitude = 128.0),
        price = 1800, distanceMeters = 300.0
    )

    private val expensiveStation = NearbyStation(
        station = GasStation(id = "ST003", name = "비싼주유소", brand = "HDO", latitude = 38.0, longitude = 128.0),
        price = 2000, distanceMeters = 2000.0
    )

    @Test
    fun `WGS84 좌표로 주유소를 추천하면 점수순으로 정렬된 결과를 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), eq(5000), eq(FuelType.GASOLINE), eq(SortType.PRICE)))
            .thenReturn(listOf(cheapStation, closeStation, expensiveStation))

        mockMvc.get("/api/gas-stations/recommendations/radius") {
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
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
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `주변에 주유소가 없으면 빈 배열을 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), any(), any(), any()))
            .thenReturn(emptyList())

        mockMvc.get("/api/gas-stations/recommendations/radius") {
            param("latitude", "38.0")
            param("longitude", "128.0")
            param("radius", "5000")
            param("fuelType", "GASOLINE")
            param("refuelLiters", "40.0")
            param("fuelEfficiency", "10.0")
            param("limit", "3")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }
}
