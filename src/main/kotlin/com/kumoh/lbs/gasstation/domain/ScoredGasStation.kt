package com.kumoh.lbs.gasstation.domain

class ScoredGasStation(
    val station: GasStation,
    val score: Double,
    val price: Int,
    val distance: Double,
    val isActualDetour: Boolean = false  // false: 직선거리 추정, true: Kakao API 실제 우회거리
) {
    companion object {
        private const val METERS_PER_KM = 1000.0

        fun of(
            station: GasStation,
            price: Int,
            distance: Double,
            refuelLiters: Double,
            fuelEfficiency: Double,
            isActualDetour: Boolean = false
        ): ScoredGasStation {
            val score = price * refuelLiters + (distance / METERS_PER_KM / fuelEfficiency) * price
            return ScoredGasStation(station, score, price, distance, isActualDetour)
        }
    }
}
