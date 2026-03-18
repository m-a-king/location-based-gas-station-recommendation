package com.kumoh.lbs.domain

data class ScoredGasStation(
    val station: GasStation,
    val score: Double,
    val frontRoadSpeed: Double
)
