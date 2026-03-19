package com.kumoh.lbs.service

import com.kumoh.lbs.client.ItsClient
import com.kumoh.lbs.client.TrafficLink
import com.kumoh.lbs.domain.BoundingBox
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.service.NearestLinkFinder.LinkMatchResult
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

        val result = nearestLinkFinder.findNearestLinkId(location)
        if (result is LinkMatchResult.Found) {
            val speed = links.firstOrNull { it.linkId == result.linkId }?.speedAsDouble()
            if (speed != null && speed > 0) return speed
            logger.debug { "ITS 응답에 매칭 linkId=${result.linkId} 없음, fallback" }
        }

        return fallbackSpeed(links)
    }

    private fun fallbackSpeed(links: List<TrafficLink>): Double =
        links
            .map { it.speedAsDouble() }
            .filter { it > 0 }
            .minOrNull() ?: 0.0
}
