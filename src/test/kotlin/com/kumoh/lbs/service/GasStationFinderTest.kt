package com.kumoh.lbs.service

import com.kumoh.lbs.client.ItsClient
import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.TrafficLink
import com.kumoh.lbs.domain.GasStation
import com.kumoh.lbs.domain.LatLon
import com.kumoh.lbs.domain.strategy.PriceDistanceStrategy
import com.kumoh.lbs.service.NearestLinkFinder.LinkMatchResult
import com.kumoh.lbs.util.CoordinateConverter
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class GasStationFinderTest {

    @Mock
    lateinit var opinetClient: OpinetClient

    @Mock
    lateinit var itsClient: ItsClient

    @Mock
    lateinit var coordinateConverter: CoordinateConverter

    @Mock
    lateinit var nearestLinkFinder: NearestLinkFinder

    @Spy
    var scoringStrategy: PriceDistanceStrategy = PriceDistanceStrategy()

    @InjectMocks
    lateinit var gasStationFinder: GasStationFinder

    private fun stubCoordinateAndTraffic() {
        whenever(coordinateConverter.katecToWgs84(any(), any()))
            .thenReturn(LatLon(37.0, 127.0))
        whenever(itsClient.getTrafficInfo(any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(nearestLinkFinder.findNearestLinkId(any(), any(), any(), any()))
            .thenReturn(LinkMatchResult.NotFound)
    }

    @Test
    fun `가격이 가장 싼 주유소가 1위로 반환된다`() {
        stubCoordinateAndTraffic()
        val stations = listOf(
            gasStation(id = "1", name = "비싼주유소", price = 1800, distance = 100.0),
            gasStation(id = "2", name = "싼주유소", price = 1500, distance = 100.0),
            gasStation(id = "3", name = "중간주유소", price = 1650, distance = 100.0)
        )
        whenever(opinetClient.searchByRadius(any(), any(), any(), any(), any())).thenReturn(stations)

        val result = gasStationFinder.findBest(100.0, 200.0, radius = 5000, fuelType = "B027", limit = 5)

        result.first().station.name shouldBe "싼주유소"
        result.last().station.name shouldBe "비싼주유소"
    }

    @Test
    fun `거리가 가까울수록 더 좋은 점수를 받는다`() {
        stubCoordinateAndTraffic()
        val stations = listOf(
            gasStation(id = "1", name = "먼주유소", price = 1600, distance = 3000.0),
            gasStation(id = "2", name = "가까운주유소", price = 1600, distance = 500.0)
        )
        whenever(opinetClient.searchByRadius(any(), any(), any(), any(), any())).thenReturn(stations)

        val result = gasStationFinder.findBest(100.0, 200.0, radius = 5000, fuelType = "B027", limit = 5)

        result.first().station.name shouldBe "가까운주유소"
    }

    @Test
    fun `limit만큼만 결과를 반환한다`() {
        stubCoordinateAndTraffic()
        val stations = (1..10).map {
            gasStation(id = "$it", name = "주유소$it", price = 1500 + it * 10, distance = 100.0)
        }
        whenever(opinetClient.searchByRadius(any(), any(), any(), any(), any())).thenReturn(stations)

        val result = gasStationFinder.findBest(100.0, 200.0, radius = 5000, fuelType = "B027", limit = 3)

        result shouldHaveSize 3
    }

    @Test
    fun `검색 결과가 없으면 빈 리스트를 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), any(), any(), any(), any())).thenReturn(emptyList())

        val result = gasStationFinder.findBest(100.0, 200.0, radius = 5000, fuelType = "B027", limit = 5)

        result.shouldBeEmpty()
    }

    @Test
    fun `매칭된 linkId의 ITS 속도를 사용한다`() {
        whenever(coordinateConverter.katecToWgs84(any(), any()))
            .thenReturn(LatLon(37.0, 127.0))
        whenever(nearestLinkFinder.findNearestLinkId(any(), any(), any(), any()))
            .thenReturn(LinkMatchResult.Found("MATCHED_LINK"))
        whenever(itsClient.getTrafficInfo(any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    trafficLink("OTHER_LINK", "10.0"),   // 느린 도로 (fallback이면 이게 선택됨)
                    trafficLink("MATCHED_LINK", "50.0")   // 매칭된 도로
                )
            )

        val stations = listOf(gasStation(id = "1", name = "테스트주유소", price = 1600, distance = 500.0))
        whenever(opinetClient.searchByRadius(any(), any(), any(), any(), any())).thenReturn(stations)

        val result = gasStationFinder.findBest(100.0, 200.0, radius = 5000, fuelType = "B027", limit = 5)

        // 속도 50 → penalty = (100-50)*2 = 100, score = 1600 + 500*0.5 + 100 = 1950
        // fallback(속도 10)이었다면 → penalty = (100-10)*2 = 180, score = 2030
        result.first().score.shouldBeWithinPercentageOf(1950.0, 0.01)
    }

    @Test
    fun `ITS 응답에 매칭 linkId가 없으면 최저 속도로 fallback한다`() {
        whenever(coordinateConverter.katecToWgs84(any(), any()))
            .thenReturn(LatLon(37.0, 127.0))
        whenever(nearestLinkFinder.findNearestLinkId(any(), any(), any(), any()))
            .thenReturn(LinkMatchResult.Found("MISSING_LINK"))
        whenever(itsClient.getTrafficInfo(any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    trafficLink("LINK_A", "30.0"),
                    trafficLink("LINK_B", "20.0")
                )
            )

        val stations = listOf(gasStation(id = "1", name = "테스트주유소", price = 1600, distance = 500.0))
        whenever(opinetClient.searchByRadius(any(), any(), any(), any(), any())).thenReturn(stations)

        val result = gasStationFinder.findBest(100.0, 200.0, radius = 5000, fuelType = "B027", limit = 5)

        // fallback 최저 속도 20 → penalty = (100-20)*2 = 160, score = 1600 + 250 + 160 = 2010
        result.first().score.shouldBeWithinPercentageOf(2010.0, 0.01)
    }

    @Test
    fun `DB에서 링크를 못 찾으면 최저 속도로 fallback한다`() {
        whenever(coordinateConverter.katecToWgs84(any(), any()))
            .thenReturn(LatLon(37.0, 127.0))
        whenever(nearestLinkFinder.findNearestLinkId(any(), any(), any(), any()))
            .thenReturn(LinkMatchResult.NotFound)
        whenever(itsClient.getTrafficInfo(any(), any(), any(), any()))
            .thenReturn(
                listOf(
                    trafficLink("LINK_A", "40.0"),
                    trafficLink("LINK_B", "15.0")
                )
            )

        val stations = listOf(gasStation(id = "1", name = "테스트주유소", price = 1600, distance = 500.0))
        whenever(opinetClient.searchByRadius(any(), any(), any(), any(), any())).thenReturn(stations)

        val result = gasStationFinder.findBest(100.0, 200.0, radius = 5000, fuelType = "B027", limit = 5)

        // fallback 최저 속도 15 → penalty = (100-15)*2 = 170, score = 1600 + 250 + 170 = 2020
        result.first().score.shouldBeWithinPercentageOf(2020.0, 0.01)
    }

    private fun gasStation(
        id: String,
        name: String,
        price: Int,
        distance: Double
    ) = GasStation(
        id = id,
        name = name,
        brand = "SKE",
        katecX = 100.0,
        katecY = 200.0,
        price = price,
        distance = distance
    )

    private fun trafficLink(linkId: String, speed: String) = TrafficLink(
        roadName = "테스트도로",
        linkId = linkId,
        speed = speed,
        travelTime = "60",
        createdDate = "2026-03-17"
    )
}
