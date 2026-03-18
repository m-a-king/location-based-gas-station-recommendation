package com.kumoh.lbs.service

import com.kumoh.lbs.client.ItsClient
import com.kumoh.lbs.domain.Coordinate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FrontRoadSpeedFinder(
    private val itsClient: ItsClient,
    private val nearestLinkFinder: NearestLinkFinder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SEARCH_RADIUS_METERS = 200
        private const val METERS_PER_DEGREE = 111_000.0
    }

    fun findSpeed(stationLocation: Coordinate): Double {
        val searchRadiusDegrees = SEARCH_RADIUS_METERS / METERS_PER_DEGREE

        val links = itsClient.searchTrafficLinks(
            minX = stationLocation.wgs84.longitude - searchRadiusDegrees,
            maxX = stationLocation.wgs84.longitude + searchRadiusDegrees,
            minY = stationLocation.wgs84.latitude - searchRadiusDegrees,
            maxY = stationLocation.wgs84.latitude + searchRadiusDegrees
        )

        val matchResult = nearestLinkFinder.findNearestLinkId(stationLocation)

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
