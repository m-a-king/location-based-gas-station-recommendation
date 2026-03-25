package com.kumoh.lbs.domain

data class Route(
    val polyline: List<Coordinate>,
    val distanceMeters: Int
)
