package com.kumoh.lbs.service

import com.kumoh.lbs.client.ItsClient
import com.kumoh.lbs.util.CoordinateConverter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FrontRoadSpeedFinder(
    private val itsClient: ItsClient,
    private val nearestLinkFinder: NearestLinkFinder,
    private val coordinateConverter: CoordinateConverter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SEARCH_RADIUS_METERS = 200
        private const val METERS_PER_DEGREE = 111_000.0
    }

    fun findSpeed(stationKatecX: Double, stationKatecY: Double): Double {
        val stationWgs84 = coordinateConverter.katecToWgs84(stationKatecX, stationKatecY)
        val searchRadiusDegrees = SEARCH_RADIUS_METERS / METERS_PER_DEGREE

        val links = itsClient.getTrafficInfo(
            minX = stationWgs84.longitude - searchRadiusDegrees,
            maxX = stationWgs84.longitude + searchRadiusDegrees,
            minY = stationWgs84.latitude - searchRadiusDegrees,
            maxY = stationWgs84.latitude + searchRadiusDegrees
        )

        val matchResult = nearestLinkFinder.findNearestLinkId(stationKatecX, stationKatecY)

        when (matchResult) {
            is NearestLinkFinder.LinkMatchResult.Found -> {
                val matchedSpeed = links
                    .firstOrNull { it.linkId == matchResult.linkId }
                    ?.speedAsDouble()
                if (matchedSpeed != null && matchedSpeed > 0) {
                    return matchedSpeed
                }
                log.debug("ITS 응답에 매칭 linkId={} 없음, fallback", matchResult.linkId)
            }

            is NearestLinkFinder.LinkMatchResult.NotFound -> {
                log.debug("주변 링크 매칭 실패, fallback")
            }
        }

        val validSpeeds = links.map { it.speedAsDouble() }.filter { it > 0 }
        if (validSpeeds.isEmpty()) return 0.0
        return validSpeeds.min()
    }
}
