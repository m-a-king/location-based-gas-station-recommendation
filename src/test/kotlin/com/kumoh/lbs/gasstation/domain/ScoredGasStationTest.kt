package com.kumoh.lbs.gasstation.domain

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScoredGasStationTest {

    private val station = GasStation(id = "S1", name = "테스트", brand = "SKE", latitude = 37.0, longitude = 127.0)

    private fun scored(
        price: Int = 1500,
        detourDistanceMeters: Double = 0.0,
        detourSeconds: Int = 0,
        refuelLiters: Double = 40.0,
        fuelEfficiency: Double = 10.0,
        isActualDetour: Boolean = false
    ) = ScoredGasStation(
        station = station,
        price = price,
        detourDistanceMeters = detourDistanceMeters,
        detourSeconds = detourSeconds,
        refuelLiters = refuelLiters,
        fuelEfficiency = fuelEfficiency,
        isActualDetour = isActualDetour
    )

    // ─── 점수 공식: price × refuelLiters + (detourKm / fuelEfficiency) × price + 시간비

    @Test
    fun `점수는 주유비와 이동 연료비의 합이다`() {
        // price=1500, refuelLiters=40, detour=2000m, fuelEfficiency=10
        // 주유비 = 1500 × 40 = 60000
        // 이동연료비 = (2000/1000/10) × 1500 = 300
        // 합계 = 60300
        val s = scored(price = 1500, detourDistanceMeters = 2000.0)

        s.score shouldBe (60300.0 plusOrMinus 0.001)
    }

    @Test
    fun `거리가 0이면 점수는 주유비만이다`() {
        val s = scored(price = 1600, detourDistanceMeters = 0.0)

        s.score shouldBe (64000.0 plusOrMinus 0.001)
    }

    @Test
    fun `가격이 높을수록 점수가 높다 (불리하다)`() {
        val cheap     = scored(price = 1500, detourDistanceMeters = 1000.0)
        val expensive = scored(price = 1800, detourDistanceMeters = 1000.0)

        (cheap.score < expensive.score) shouldBe true
    }

    @Test
    fun `거리가 멀수록 점수가 높다 (불리하다)`() {
        val near = scored(price = 1500, detourDistanceMeters = 500.0)
        val far  = scored(price = 1500, detourDistanceMeters = 5000.0)

        (near.score < far.score) shouldBe true
    }

    @Test
    fun `우회 시간이 길수록 점수가 높다 (불리하다)`() {
        val quick = scored(detourSeconds = 0)
        val slow  = scored(detourSeconds = 600)

        (quick.score < slow.score) shouldBe true
    }

    // ─── isActualDetour 플래그 ───────────────────────────────────────────────

    @Test
    fun `기본값은 직선거리 추정이다 (isActualDetour=false)`() {
        val s = scored(detourDistanceMeters = 1000.0)

        s.isActualDetour shouldBe false
    }

    @Test
    fun `isActualDetour=true로 생성하면 실제 우회거리임을 나타낸다`() {
        val s = scored(detourDistanceMeters = 1000.0, isActualDetour = true)

        s.isActualDetour shouldBe true
    }
}
