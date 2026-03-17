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
        stationKatecY: Double,
        userKatecX: Double,
        userKatecY: Double
    ): LinkMatchResult {
        val stationWgs = coordinateConverter.katecToWgs84(stationKatecX, stationKatecY)
        val userWgs = coordinateConverter.katecToWgs84(userKatecX, userKatecY)

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

        val approachDirectionX = stationWgs.longitude - userWgs.longitude
        val approachDirectionY = stationWgs.latitude - userWgs.latitude

        val scored = candidates.map { link ->
            val distanceToLink = pointToSegmentDistance(
                stationWgs.longitude, stationWgs.latitude,
                link.fLongitude, link.fLatitude,
                link.tLongitude, link.tLatitude
            )
            val linkDirectionX = link.tLongitude - link.fLongitude
            val linkDirectionY = link.tLatitude - link.fLatitude
            val directionSimilarity = cosineSimilarity(
                approachDirectionX, approachDirectionY,
                linkDirectionX, linkDirectionY
            )
            ScoredLink(link.linkId, distanceToLink, directionSimilarity)
        }

        val sameDirectionLinks = scored.filter { it.directionSimilarity > 0 }
        val best = if (sameDirectionLinks.isNotEmpty()) {
            sameDirectionLinks.minBy { it.distanceToStation }
        } else {
            scored.minBy { it.distanceToStation }
        }

        log.debug(
            "주유소 진입 도로 매칭: linkId={}, distance={:.6f}, directionSimilarity={:.3f}",
            best.linkId, best.distanceToStation, best.directionSimilarity
        )
        return LinkMatchResult.Found(best.linkId)
    }

    private data class ScoredLink(val linkId: String, val distanceToStation: Double, val directionSimilarity: Double)
}

/** 두 2D 벡터의 코사인 유사도를 계산합니다. */
fun cosineSimilarity(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dot = ax * bx + ay * by
    val magA = sqrt(ax * ax + ay * ay)
    val magB = sqrt(bx * bx + by * by)
    if (magA == 0.0 || magB == 0.0) return 0.0
    return dot / (magA * magB)
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
