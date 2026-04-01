package com.kumoh.lbs.gasstation.domain

import com.kumoh.lbs.geo.Coordinate

data class Route(
    val polyline: List<Coordinate>,
    val distanceMeters: Int
) {
    init {
        require(polyline.isNotEmpty()) { "경로 폴리라인이 비어 있습니다." }
        require(distanceMeters > 0) { "경로 거리는 양수여야 합니다." }
    }
}
