package com.kumoh.lbs.gasstation.domain

data class RouteStationCandidate(
    val station: GasStation,
    val price: Int,
    val distanceFromRoute: Double
)
