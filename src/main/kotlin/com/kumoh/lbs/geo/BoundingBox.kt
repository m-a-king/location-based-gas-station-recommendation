package com.kumoh.lbs.geo

import kotlin.math.cos

class BoundingBox private constructor(
    val southWest: Coordinate,
    val northEast: Coordinate
) {
    val minLat: Double get() = southWest.wgs84.latitude
    val maxLat: Double get() = northEast.wgs84.latitude
    val minLon: Double get() = southWest.wgs84.longitude
    val maxLon: Double get() = northEast.wgs84.longitude

    companion object {
        private const val METERS_PER_LATITUDE_DEGREE = 111_320.0

        fun around(center: Coordinate, radiusMeters: Int): BoundingBox {
            val latDelta = radiusMeters / METERS_PER_LATITUDE_DEGREE
            val lonDelta = radiusMeters / (METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(center.wgs84.latitude)))
            return BoundingBox(
                southWest = Coordinate.fromWgs84(Coordinate.Wgs84(center.wgs84.latitude - latDelta, center.wgs84.longitude - lonDelta)),
                northEast = Coordinate.fromWgs84(Coordinate.Wgs84(center.wgs84.latitude + latDelta, center.wgs84.longitude + lonDelta))
            )
        }

        fun aroundPolyline(polyline: List<Coordinate>, bufferMeters: Double): BoundingBox {
            val lats = polyline.map { it.wgs84.latitude }
            val lons = polyline.map { it.wgs84.longitude }
            val midLat = (lats.min() + lats.max()) / 2.0
            val latDelta = bufferMeters / METERS_PER_LATITUDE_DEGREE
            val lonDelta = latDelta / cos(Math.toRadians(midLat))
            return BoundingBox(
                southWest = Coordinate.fromWgs84(Coordinate.Wgs84(lats.min() - latDelta, lons.min() - lonDelta)),
                northEast = Coordinate.fromWgs84(Coordinate.Wgs84(lats.max() + latDelta, lons.max() + lonDelta))
            )
        }
    }
}
