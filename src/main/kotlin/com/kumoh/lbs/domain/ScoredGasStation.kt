package com.kumoh.lbs.domain

class ScoredGasStation(
    val station: GasStation,
    val score: Double,
    val frontRoadSpeed: Double = 0.0,
    val detourDistance: Double? = null
) {
    fun withFrontRoadSpeed(speed: Double) = ScoredGasStation(station, score, speed, detourDistance)

    companion object {
        private const val METERS_PER_KM = 1000.0

        /**
         * 점수 = 총 주유비 + 이동 연료비 (낮을수록 좋음)
         *
         * - 총 주유비: 리터당 가격 × 주유량
         * - 이동 연료비: (거리m / 1000 / 연비km/L) × 리터당 가격
         *
         * @param refuelLiters 주유량 (L)
         * @param fuelEfficiency 차량 연비 (km/L)
         */
        fun of(
            station: GasStation,
            refuelLiters: Double,
            fuelEfficiency: Double
        ): ScoredGasStation {
            val totalFuelCost = station.price * refuelLiters
            val distanceKm = station.distance / METERS_PER_KM
            val tripFuelCost = (distanceKm / fuelEfficiency) * station.price
            val score = totalFuelCost + tripFuelCost
            return ScoredGasStation(station, score)
        }

        /**
         * 경로 기반 점수 = 총 주유비 + 우회 연료비 (낮을수록 좋음)
         *
         * - 총 주유비: 리터당 가격 × 주유량
         * - 우회 연료비: (우회거리m / 1000 / 연비km/L) × 리터당 가격
         *
         * @param refuelLiters 주유량 (L)
         * @param fuelEfficiency 차량 연비 (km/L)
         * @param detourDistance 경로 이탈 왕복 거리 (m)
         */
        fun ofWithDetour(
            station: GasStation,
            refuelLiters: Double,
            fuelEfficiency: Double,
            detourDistance: Double
        ): ScoredGasStation {
            val totalFuelCost = station.price * refuelLiters
            val detourKm = detourDistance / METERS_PER_KM
            val detourFuelCost = (detourKm / fuelEfficiency) * station.price
            val score = totalFuelCost + detourFuelCost
            return ScoredGasStation(station, score, detourDistance = detourDistance)
        }
    }
}
