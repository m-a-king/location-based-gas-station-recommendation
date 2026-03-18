package com.kumoh.lbs.service

import com.kumoh.lbs.client.ItsClient
import com.kumoh.lbs.domain.BoundingBox
import com.kumoh.lbs.domain.Coordinate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TrafficSpeedFinder(
    private val itsClient: ItsClient,
    private val nearestLinkFinder: NearestLinkFinder
) {

    companion object {
        private const val SEARCH_RADIUS_METERS = 200
    }

    fun findAt(location: Coordinate): Double {
        val box = BoundingBox.around(location, SEARCH_RADIUS_METERS)

        val links = itsClient.searchTrafficLinks(
            minX = box.southWest.wgs84.longitude,
            maxX = box.northEast.wgs84.longitude,
            minY = box.southWest.wgs84.latitude,
            maxY = box.northEast.wgs84.latitude
        )

        val matchResult = nearestLinkFinder.findNearestLinkId(location)

        when (matchResult) {
            is NearestLinkFinder.LinkMatchResult.Found -> {
                val matchedSpeed = links
                    .firstOrNull { it.linkId == matchResult.linkId }
                    ?.speedAsDouble()
                if (matchedSpeed != null && matchedSpeed > 0) {
                    return matchedSpeed
                }
                logger.debug { "ITS 응답에 매칭 linkId=${matchResult.linkId} 없음, fallback" }
            }

            is NearestLinkFinder.LinkMatchResult.NotFound -> {
                logger.debug { "주변 링크 매칭 실패, fallback" }
            }
        }

        val validSpeeds = links.map { it.speedAsDouble() }.filter { it > 0 }
        if (validSpeeds.isEmpty()) return 0.0
        return validSpeeds.min()
    }
}
