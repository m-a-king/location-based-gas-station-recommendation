package com.kumoh.lbs.domain

class ScoredGasStation private constructor(
    val station: GasStation,
    val score: Double,
    val trafficSpeed: Double
) {
    companion object {
        private const val DISTANCE_WEIGHT = 0.5

        fun of(station: GasStation, trafficSpeed: Double): ScoredGasStation {
            val score = station.price.toDouble() + station.distance * DISTANCE_WEIGHT
            return ScoredGasStation(station, score, trafficSpeed)
        }
    }
}
