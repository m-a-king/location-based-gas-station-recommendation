package com.kumoh.lbs.gasstation.domain

class ScoredGasStation(
    val priced: PricedGasStation,
    val detourDistanceMeters: Double,
    val detourSeconds: Int,
    val refuelLiters: Double,
    val fuelEfficiency: Double,
    val isActualDetour: Boolean = false
) {
    val station: GasStation get() = priced.station
    val price: Int get() = priced.price

    val detourKm: Double
        get() = detourDistanceMeters / METERS_PER_KM

    val fuelCost: Double
        get() = price * refuelLiters

    val detourFuelCost: Double
        get() = (detourKm / fuelEfficiency) * price

    val detourTimeCost: Double
        get() = (detourSeconds / SECONDS_PER_HOUR) * MINIMUM_WAGE_PER_HOUR

    val score: Double
        get() = fuelCost + detourFuelCost + detourTimeCost

    companion object {
        private const val METERS_PER_KM = 1000.0
        private const val SECONDS_PER_HOUR = 3600.0

        // 2026년 최저시급 (원/시간) — 우회 시간 비용 산정 기준
        private const val MINIMUM_WAGE_PER_HOUR = 10320.0
    }
}
