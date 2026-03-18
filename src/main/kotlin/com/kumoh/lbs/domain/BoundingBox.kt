package com.kumoh.lbs.domain

class BoundingBox private constructor(
    val southWest: Coordinate,
    val northEast: Coordinate
) {
    companion object {
        private const val METERS_PER_DEGREE = 111_000.0

        fun around(center: Coordinate, radiusMeters: Int): BoundingBox {
            val radiusDegrees = radiusMeters / METERS_PER_DEGREE
            return BoundingBox(
                southWest = Coordinate.fromWgs84(Coordinate.Wgs84(
                    latitude = center.wgs84.latitude - radiusDegrees,
                    longitude = center.wgs84.longitude - radiusDegrees
                )),
                northEast = Coordinate.fromWgs84(Coordinate.Wgs84(
                    latitude = center.wgs84.latitude + radiusDegrees,
                    longitude = center.wgs84.longitude + radiusDegrees
                ))
            )
        }
    }
}
