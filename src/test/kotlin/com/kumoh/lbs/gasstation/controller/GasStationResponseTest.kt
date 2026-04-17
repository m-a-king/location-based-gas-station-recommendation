package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.ScoredGasStation
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GasStationResponseTest {

    private fun scored(id: String, price: Int, refuelLiters: Double = 40.0): ScoredGasStation =
        ScoredGasStation(
            station = GasStation(id = id, name = id, brand = "SKE", latitude = 37.0, longitude = 127.0),
            price = price,
            detourDistanceMeters = 0.0,
            detourSeconds = 0,
            refuelLiters = refuelLiters,
            fuelEfficiency = 10.0,
            isActualDetour = false
        )

    @Test
    fun `빈 리스트를 넘기면 빈 응답을 반환한다`() {
        GasStationResponse.fromList(emptyList()).shouldBeEmpty()
    }

    @Test
    fun `maxPriceInCandidates가 null이면 scoredList 최고가로 절감액을 계산한다`() {
        val list = listOf(scored("A", 1500), scored("B", 1800))

        val result = GasStationResponse.fromList(list, maxPriceInCandidates = null)

        result shouldHaveSize 2
        // scored 최고가 1800 - 자기 가격 1500 -> 40L × 300 = 12000
        result[0].estimatedSavings shouldBe 12000
        result[1].estimatedSavings shouldBe 0
    }

    @Test
    fun `maxPriceInCandidates가 주어지면 scoredList와 별개로 해당 값을 기준으로 계산한다`() {
        val list = listOf(scored("A", 1500))
        // 후보군 최고가는 2000이나 scored에는 1500만 살아남음
        val result = GasStationResponse.fromList(list, maxPriceInCandidates = 2000)

        result[0].estimatedSavings shouldBe (2000 - 1500) * 40
    }

    @Test
    fun `maxPriceInCandidates 인자를 생략하면 null과 동일하게 동작한다`() {
        val list = listOf(scored("A", 1500), scored("B", 1600))

        val omitted = GasStationResponse.fromList(list)
        val explicitNull = GasStationResponse.fromList(list, maxPriceInCandidates = null)

        omitted.map { it.estimatedSavings } shouldBe explicitNull.map { it.estimatedSavings }
    }
}
