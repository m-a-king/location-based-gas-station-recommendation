package com.kumoh.lbs.service

import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import com.kumoh.lbs.domain.GasStation
import com.kumoh.lbs.domain.ScoredGasStation
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class GasStationRadiusRecommenderTest {

    @Mock
    lateinit var opinetClient: OpinetClient

    @Mock
    lateinit var trafficSpeedFinder: TrafficSpeedFinder

    @InjectMocks
    lateinit var radiusRecommender: GasStationRadiusRecommender

    private val defaultRefuelLiters = 40.0
    private val defaultFuelEfficiency = 10.0

    private fun stubTrafficSpeed(speed: Double = 0.0) {
        whenever(trafficSpeedFinder.findAt(any())).thenReturn(speed)
    }

    private fun recommend(stations: List<GasStation>, limit: Int = 5): List<ScoredGasStation> {
        stubTrafficSpeed()
        return radiusRecommender.recommend(
            Coordinate.fromKatec(Coordinate.Katec(100.0, 200.0)),
            radius = 5000, fuelType = FuelType.GASOLINE,
            refuelLiters = defaultRefuelLiters,
            fuelEfficiency = defaultFuelEfficiency,
            limit = limit
        )
    }

    @Test
    fun `가격이 가장 싼 주유소가 1위로 반환된다`() {
        val stations = listOf(
            gasStation(id = "1", name = "비싼주유소", price = 1800, distance = 100.0),
            gasStation(id = "2", name = "싼주유소", price = 1500, distance = 100.0),
            gasStation(id = "3", name = "중간주유소", price = 1650, distance = 100.0)
        )
        whenever(opinetClient.searchByRadius(any(), any(), any(), any())).thenReturn(stations)

        val result = recommend(stations)

        result.first().station.name shouldBe "싼주유소"
        result.last().station.name shouldBe "비싼주유소"
    }

    @Test
    fun `거리가 가까울수록 더 좋은 점수를 받는다`() {
        val stations = listOf(
            gasStation(id = "1", name = "먼주유소", price = 1600, distance = 3000.0),
            gasStation(id = "2", name = "가까운주유소", price = 1600, distance = 500.0)
        )
        whenever(opinetClient.searchByRadius(any(), any(), any(), any())).thenReturn(stations)

        val result = recommend(stations)

        result.first().station.name shouldBe "가까운주유소"
    }

    @Test
    fun `limit만큼만 결과를 반환한다`() {
        val stations = (1..10).map {
            gasStation(id = "$it", name = "주유소$it", price = 1500 + it * 10, distance = 100.0)
        }
        whenever(opinetClient.searchByRadius(any(), any(), any(), any())).thenReturn(stations)

        val result = recommend(stations, limit = 3)

        result shouldHaveSize 3
    }

    @Test
    fun `검색 결과가 없으면 빈 리스트를 반환한다`() {
        whenever(opinetClient.searchByRadius(any(), any(), any(), any())).thenReturn(emptyList())

        val result = radiusRecommender.recommend(
            Coordinate.fromKatec(Coordinate.Katec(100.0, 200.0)),
            radius = 5000, fuelType = FuelType.GASOLINE,
            refuelLiters = defaultRefuelLiters,
            fuelEfficiency = defaultFuelEfficiency,
            limit = 5
        )

        result.shouldBeEmpty()
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
        location = Coordinate.fromKatec(Coordinate.Katec(100.0, 200.0)),
        price = price,
        distance = distance
    )
}
