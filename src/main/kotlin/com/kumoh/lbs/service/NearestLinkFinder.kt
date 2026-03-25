package com.kumoh.lbs.service

import com.kumoh.lbs.domain.BoundingBox
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.repository.MoctLinkRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import kotlin.math.cos
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

@Service
class NearestLinkFinder(
    private val moctLinkRepository: MoctLinkRepository
) {

    companion object {
        private const val SEARCH_RADIUS_METERS = 200
        private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
    }

    sealed interface LinkMatchResult {
        data class Found(val linkId: String) : LinkMatchResult
        data object NotFound : LinkMatchResult
    }

    fun findNearestLinkId(stationLocation: Coordinate): LinkMatchResult {
        val box = BoundingBox.around(stationLocation, SEARCH_RADIUS_METERS)
        val candidates = moctLinkRepository.findLinksIn(box)

        if (candidates.isEmpty()) {
            logger.debug { "주유소 주변 링크 없음: katec=(${stationLocation.katec.x}, ${stationLocation.katec.y})" }
            return LinkMatchResult.NotFound
        }

        val lat = stationLocation.wgs84.latitude
        val lonScale = cos(Math.toRadians(lat))

        fun distanceMeters(
            fLon: Double,
            fLat: Double,
            tLon: Double,
            tLat: Double
        ): Double {
            val degrees = pointToSegmentDistance(
                stationLocation.wgs84.longitude, stationLocation.wgs84.latitude,
                fLon, fLat, tLon, tLat, lonScale
            )
            return degrees * METERS_PER_LATITUDE_DEGREE
        }

        val best = candidates.minBy {
            distanceMeters(it.fLongitude, it.fLatitude, it.tLongitude, it.tLatitude)
        }

        val meters = distanceMeters(best.fLongitude, best.fLatitude, best.tLongitude, best.tLatitude)
        if (meters > SEARCH_RADIUS_METERS) {
            logger.debug { "최근접 링크 거리 ${meters}m > ${SEARCH_RADIUS_METERS}m, 범위 초과" }
            return LinkMatchResult.NotFound
        }

        logger.debug { "주유소 앞 도로 매칭: linkId=${best.linkId}" }
        return LinkMatchResult.Found(best.linkId)
    }
}

/**
 * 점 (px, py)에서 선분까지의 거리를 degree 단위로 반환합니다.
 * lonScale로 경도 축을 위도 기준 cos 보정하여 동서 방향 왜곡을 보정합니다.
 * lonScale이 1.0이면 보정 없이 순수 degree 거리입니다.
 */
fun pointToSegmentDistance(
    px: Double, py: Double,
    segStartX: Double, segStartY: Double,
    segEndX: Double, segEndY: Double,
    lonScale: Double = 1.0
): Double {
    val segDx = (segEndX - segStartX) * lonScale
    val segDy = segEndY - segStartY
    if (segDx == 0.0 && segDy == 0.0) {
        val gapX = (px - segStartX) * lonScale
        val gapY = py - segStartY
        return sqrt(gapX * gapX + gapY * gapY)
    }
    val projection = (((px - segStartX) * lonScale) * segDx + (py - segStartY) * segDy) / (segDx * segDx + segDy * segDy)
    val clamped = projection.coerceIn(0.0, 1.0)
    val closestX = segStartX + clamped * (segEndX - segStartX)
    val closestY = segStartY + clamped * (segEndY - segStartY)
    val gapX = (px - closestX) * lonScale
    val gapY = py - closestY
    return sqrt(gapX * gapX + gapY * gapY)
}
