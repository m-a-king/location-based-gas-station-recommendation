package com.kumoh.lbs.service

import com.kumoh.lbs.domain.BoundingBox
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.repository.MoctLinkRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

@Service
class NearestLinkFinder(
    private val moctLinkRepository: MoctLinkRepository
) {

    companion object {
        private const val SEARCH_RADIUS_METERS = 200
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

        val best = candidates.minBy { link ->
            pointToSegmentDistance(
                stationLocation.wgs84.longitude, stationLocation.wgs84.latitude,
                link.fLongitude, link.fLatitude,
                link.tLongitude, link.tLatitude
            )
        }

        logger.debug { "주유소 앞 도로 매칭: linkId=${best.linkId}" }
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
