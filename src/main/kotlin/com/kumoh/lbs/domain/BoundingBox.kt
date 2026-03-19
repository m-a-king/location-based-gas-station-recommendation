package com.kumoh.lbs.domain

import kotlin.math.cos

class BoundingBox private constructor(
    val southWest: Coordinate,
    val northEast: Coordinate
) {
    companion object {
        private const val METERS_PER_LATITUDE_DEGREE = 111_320.0

        fun around(center: Coordinate, radiusMeters: Int): BoundingBox {
            val latRadiusDegrees = radiusMeters / METERS_PER_LATITUDE_DEGREE
            val lonRadiusDegrees = radiusMeters / (METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(center.wgs84.latitude)))
            return BoundingBox(
                southWest = Coordinate.fromWgs84(Coordinate.Wgs84(
                    latitude = center.wgs84.latitude - latRadiusDegrees,
                    longitude = center.wgs84.longitude - lonRadiusDegrees
                )),
                northEast = Coordinate.fromWgs84(Coordinate.Wgs84(
                    latitude = center.wgs84.latitude + latRadiusDegrees,
                    longitude = center.wgs84.longitude + lonRadiusDegrees
                ))
            )
        }
    }
}
