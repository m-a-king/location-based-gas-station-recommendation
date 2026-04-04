package com.kumoh.lbs.gasstation.domain

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScoredGasStationTest {

    private val station = GasStation(id = "S1", name = "테스트", brand = "SKE", latitude = 37.0, longitude = 127.0)

    // ─── 점수 공식: price × refuelLiters + (distance / 1000 / fuelEfficiency) × price

    @Test
    fun `점수는 주유비와 이동 연료비의 합이다`() {
        // price=1500, refuelLiters=40, distance=2000m, fuelEfficiency=10
        // 주유비 = 1500 × 40 = 60000
        // 이동연료비 = (2000/1000/10) × 1500 = 300
        // 합계 = 60300
        val scored = ScoredGasStation.of(station, price = 1500, distance = 2000.0, refuelLiters = 40.0, fuelEfficiency = 10.0)

        scored.score shouldBe (60300.0 plusOrMinus 0.001)
    }

    @Test
    fun `거리가 0이면 점수는 주유비만이다`() {
        val scored = ScoredGasStation.of(station, price = 1600, distance = 0.0, refuelLiters = 40.0, fuelEfficiency = 10.0)

        scored.score shouldBe (64000.0 plusOrMinus 0.001)
    }

    @Test
    fun `가격이 높을수록 점수가 높다 (불리하다)`() {
        val cheap     = ScoredGasStation.of(station, price = 1500, distance = 1000.0, refuelLiters = 40.0, fuelEfficiency = 10.0)
        val expensive = ScoredGasStation.of(station, price = 1800, distance = 1000.0, refuelLiters = 40.0, fuelEfficiency = 10.0)

        (cheap.score < expensive.score) shouldBe true
    }

    @Test
    fun `거리가 멀수록 점수가 높다 (불리하다)`() {
        val near = ScoredGasStation.of(station, price = 1500, distance = 500.0,  refuelLiters = 40.0, fuelEfficiency = 10.0)
        val far  = ScoredGasStation.of(station, price = 1500, distance = 5000.0, refuelLiters = 40.0, fuelEfficiency = 10.0)

        (near.score < far.score) shouldBe true
    }

    // ─── isActualDetour 플래그 ───────────────────────────────────────────────

    @Test
    fun `기본값은 직선거리 추정이다 (isActualDetour=false)`() {
        val scored = ScoredGasStation.of(station, price = 1500, distance = 1000.0, refuelLiters = 40.0, fuelEfficiency = 10.0)

        scored.isActualDetour shouldBe false
    }

    @Test
    fun `isActualDetour=true로 생성하면 실제 우회거리임을 나타낸다`() {
        val scored = ScoredGasStation.of(station, price = 1500, distance = 1000.0, refuelLiters = 40.0, fuelEfficiency = 10.0, isActualDetour = true)

        scored.isActualDetour shouldBe true
    }
}
