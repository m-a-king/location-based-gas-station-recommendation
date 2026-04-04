package com.kumoh.lbs.gasstation.domain

class ScoredGasStation(
    val station: GasStation,
    val score: Double,
    val price: Int,
    val distance: Double,
    val durationSeconds: Int = 0,
    val isActualDetour: Boolean = false  // false: 직선거리 추정, true: Kakao API 실제 우회거리
) {
    companion object {
        private const val METERS_PER_KM = 1000.0
        private const val SECONDS_PER_HOUR = 3600.0

        // 2025년 최저시급 (원/시간) — 우회 시간 비용 산정 기준
        private const val MINIMUM_WAGE_PER_HOUR = 10030.0

        fun of(
            station: GasStation,
            price: Int,
            distance: Double,
            refuelLiters: Double,
            fuelEfficiency: Double,
            durationSeconds: Int = 0,
            isActualDetour: Boolean = false
        ): ScoredGasStation {
            val fuelCost = price * refuelLiters + (distance / METERS_PER_KM / fuelEfficiency) * price
            val timeCost = (durationSeconds / SECONDS_PER_HOUR) * MINIMUM_WAGE_PER_HOUR
            val score = fuelCost + timeCost
            return ScoredGasStation(station, score, price, distance, durationSeconds, isActualDetour)
        }
    }
}
