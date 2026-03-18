package com.kumoh.lbs.domain

data class GasStation(
    val id: String,
    val name: String,
    val brand: String,
    val location: Coordinate,
    val price: Int,
    val distance: Double
)
