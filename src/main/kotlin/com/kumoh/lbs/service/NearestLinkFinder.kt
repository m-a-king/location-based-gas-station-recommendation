package com.kumoh.lbs.service

import com.kumoh.lbs.repository.MoctLinkRepository
import com.kumoh.lbs.util.CoordinateConverter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.sqrt

@Service
class NearestLinkFinder(
    private val moctLinkRepository: MoctLinkRepository,
    private val coordinateConverter: CoordinateConverter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SEARCH_RADIUS_METERS = 200
        private const val METERS_PER_DEGREE = 111_000.0
    }

    sealed interface LinkMatchResult {
        data class Found(val linkId: String) : LinkMatchResult
        data object NotFound : LinkMatchResult
    }

    fun findNearestLinkId(
        stationKatecX: Double,
        stationKatecY: Double
    ): LinkMatchResult {
        val stationWgs = coordinateConverter.katecToWgs84(stationKatecX, stationKatecY)

        val searchRadiusDegrees = SEARCH_RADIUS_METERS / METERS_PER_DEGREE
        val candidates = moctLinkRepository.findLinksInBoundingBox(
            minLon = stationWgs.longitude - searchRadiusDegrees,
            maxLon = stationWgs.longitude + searchRadiusDegrees,
            minLat = stationWgs.latitude - searchRadiusDegrees,
            maxLat = stationWgs.latitude + searchRadiusDegrees
        )

        if (candidates.isEmpty()) {
            log.debug("주유소 주변 링크 없음: katec=({}, {})", stationKatecX, stationKatecY)
            return LinkMatchResult.NotFound
        }

        val best = candidates.minBy { link ->
            pointToSegmentDistance(
                stationWgs.longitude, stationWgs.latitude,
                link.fLongitude, link.fLatitude,
                link.tLongitude, link.tLatitude
            )
        }

        log.debug("주유소 앞 도로 매칭: linkId={}", best.linkId)
        return LinkMatchResult.Found(best.linkId)
    }
}

/** 점 (px, py)에서 선분 (segStartX,segStartY)-(segEndX,segEndY)까지의 최소 거리를 계산합니다. */
fun pointToSegmentDistance(
    px: Double, py: Double,
    segStartX: Double, segStartY: Double,
    segEndX: Double, segEndY: Double
): Double {
    val segDx = segEndX - segStartX
    val segDy = segEndY - segStartY
    if (segDx == 0.0 && segDy == 0.0) {
        val gapX = px - segStartX
        val gapY = py - segStartY
        return sqrt(gapX * gapX + gapY * gapY)
    }
    val projection = ((px - segStartX) * segDx + (py - segStartY) * segDy) / (segDx * segDx + segDy * segDy)
    val clampedProjection = projection.coerceIn(0.0, 1.0)
    val closestX = segStartX + clampedProjection * segDx
    val closestY = segStartY + clampedProjection * segDy
    val gapX = px - closestX
    val gapY = py - closestY
    return sqrt(gapX * gapX + gapY * gapY)
}
