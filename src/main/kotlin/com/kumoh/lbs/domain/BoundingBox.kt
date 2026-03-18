package com.kumoh.lbs.domain

class BoundingBox private constructor(
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double
) {
    companion object {
        private const val METERS_PER_DEGREE = 111_000.0

        fun around(center: Coordinate, radiusMeters: Int): BoundingBox {
            val radiusDegrees = radiusMeters / METERS_PER_DEGREE
            return BoundingBox(
                minLon = center.wgs84.longitude - radiusDegrees,
                maxLon = center.wgs84.longitude + radiusDegrees,
                minLat = center.wgs84.latitude - radiusDegrees,
                maxLat = center.wgs84.latitude + radiusDegrees
            )
        }
    }
}
