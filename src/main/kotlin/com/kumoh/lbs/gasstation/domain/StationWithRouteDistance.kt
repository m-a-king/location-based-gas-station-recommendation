package com.kumoh.lbs.gasstation.domain

data class StationWithRouteDistance(
    val station: GasStation,
    val distanceFromRoute: Double
)
