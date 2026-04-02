package com.kumoh.lbs.gasstation.domain

data class NearbyStation(
    val station: GasStation,
    val price: Int,
    val distanceMeters: Double
)
