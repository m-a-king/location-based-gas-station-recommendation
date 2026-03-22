package com.kumoh.lbs.domain

class ScoredGasStation(
    val station: GasStation,
    val score: Double,
    val trafficSpeed: Double = 0.0
) {
    fun withTrafficSpeed(speed: Double) = ScoredGasStation(station, score, speed)

    companion object {
        private const val METERS_PER_KM = 1000.0

        /**
         * 점수 = 총 주유비 + 이동 연료비 (낮을수록 좋음)
         *
         * - 총 주유비: 리터당 가격 × 주유량
         * - 이동 연료비: (거리m / 1000 / 연비km/L) × 리터당 가격
         *
         * @param fuelAmount 주유량 (L)
         * @param fuelEfficiency 차량 연비 (km/L)
         */
        fun of(station: GasStation, fuelAmount: Double, fuelEfficiency: Double): ScoredGasStation {
            val totalFuelCost = station.price * fuelAmount
            val distanceKm = station.distance / METERS_PER_KM
            val tripFuelCost = (distanceKm / fuelEfficiency) * station.price
            val score = totalFuelCost + tripFuelCost
            return ScoredGasStation(station, score)
        }
    }
}
