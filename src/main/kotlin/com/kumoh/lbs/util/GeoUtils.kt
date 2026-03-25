package com.kumoh.lbs.util

import com.kumoh.lbs.domain.Coordinate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(
        a: Coordinate.Wgs84,
        b: Coordinate.Wgs84
    ): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon

        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    fun pointToSegmentDistanceMeters(
        point: Coordinate.Wgs84,
        segStart: Coordinate.Wgs84,
        segEnd: Coordinate.Wgs84
    ): Double {
        val segDx = segEnd.longitude - segStart.longitude
        val segDy = segEnd.latitude - segStart.latitude
        val segLengthSq = segDx * segDx + segDy * segDy

        if (segLengthSq == 0.0) return haversineMeters(point, segStart)

        val projection = ((point.longitude - segStart.longitude) * segDx +
                (point.latitude - segStart.latitude) * segDy) / segLengthSq
        val t = projection.coerceIn(0.0, 1.0)

        val closest = Coordinate.Wgs84(
            latitude = segStart.latitude + t * segDy,
            longitude = segStart.longitude + t * segDx
        )

        return haversineMeters(point, closest)
    }

    fun minDistanceToPolylineMeters(
        point: Coordinate,
        polyline: List<Coordinate>
    ): Double =
        polyline.zipWithNext().minOf { (start, end) ->
            pointToSegmentDistanceMeters(point.wgs84, start.wgs84, end.wgs84)
        }
}
