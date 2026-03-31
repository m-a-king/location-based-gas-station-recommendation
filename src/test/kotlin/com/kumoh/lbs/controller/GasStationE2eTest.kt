package com.kumoh.lbs.controller

import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetClient.SortType
import com.kumoh.lbs.client.OpinetStation
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GasStationE2eTest(
    val mockMvc: MockMvc,
    @MockitoBean val opinetClient: OpinetClient
) {

    // 좌표는 반경 추천 시 opinet에 전달되므로 katec 값을 직접 사용
    private val stationKatec = Coordinate.fromWgs84(Coordinate.Wgs84(38.0, 128.0)).katec

    private val cheapStation = OpinetStation(
        stationId = "ST001", stationName = "싼주유소", brandCode = "SKE",
        price = 1600, distance = 1000.0,
        katecX = stationKatec.x, katecY = stationKatec.y
    )

    private val closeStation = OpinetStation(
        stationId = "ST002", stationName = "가까운주유소", brandCode = "GSC",
        price = 1800, distance = 300.0,
        katecX = stationKatec.x, katecY = stationKatec.y
    )

    private val expensiveStation = OpinetStation(
        stationId = "ST003", stationName = "비싼주유소", brandCode = "HDO",
        price = 2000, distance = 2000.0,
        katecX = stationKatec.x, katecY = stationKatec.y
    )

    @Test
    fun `WGS84 좌표로 주유소를 추천하면 점수순으로 정렬된 결과를 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), eq(5000), eq(FuelType.GASOLINE), eq(SortType.PRICE)))
            .thenReturn(listOf(cheapStation, closeStation, expensiveStation))

        // refuelLiters=40, fuelEfficiency=10
        // cheapStation:  총주유비=1600*40=64000, 이동연료비=(1.0/10)*1600=160  → 64160
        // closeStation:  총주유비=1800*40=72000, 이동연료비=(0.3/10)*1800=54   → 72054
        // expensiveStation: 총주유비=2000*40=80000, 이동연료비=(2.0/10)*2000=400 → 80400

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
            param("limit", "5")
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
