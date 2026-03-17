package com.kumoh.lbs.service

import com.kumoh.lbs.client.ItsClient
import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.domain.GasStation
import com.kumoh.lbs.domain.ScoredGasStation
import com.kumoh.lbs.domain.strategy.ScoringStrategy
import com.kumoh.lbs.util.CoordinateConverter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GasStationFinder(
    private val opinetClient: OpinetClient,
    private val itsClient: ItsClient,
    private val scoringStrategy: ScoringStrategy,
    private val coordinateConverter: CoordinateConverter,
    private val nearestLinkFinder: NearestLinkFinder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ROAD_SEARCH_RADIUS_METERS = 200
        private const val METERS_PER_DEGREE = 111_000.0
    }

    fun findBest(
        katecX: Double,
        katecY: Double,
        radius: Int,
        fuelType: String,
        limit: Int
    ): List<ScoredGasStation> {
        require(limit in 1..5)
        val stations = opinetClient.searchByRadius(katecX, katecY, radius, fuelType, sort = 1)
        log.info("Opinet API 응답: {} 건, katecX={}, katecY={}, radius={}", stations.size, katecX, katecY, radius)

        return stations
            .map {
                val frontRoadSpeed = fetchFrontRoadSpeed(it, katecX, katecY)
                ScoredGasStation(it, scoringStrategy.score(it, frontRoadSpeed))
            }
            .sortedBy { it.score }
            .take(limit)
    }

    /**
     * 주유소 바로 앞 진입 도로의 통행 속도를 조회합니다.
     *
     * 1. DB에서 주유소 앞 가장 가까운 링크를 찾아 linkId 특정
     * 2. ITS API에서 해당 linkId의 속도를 조회
     * 3. 매칭 실패 시 기존 방식(영역 내 최저 속도)으로 fallback
     */
    private fun fetchFrontRoadSpeed(station: GasStation, userKatecX: Double, userKatecY: Double): Double {
        val stationWgs84 = coordinateConverter.katecToWgs84(station.katecX, station.katecY)
        val searchRadiusDegrees = ROAD_SEARCH_RADIUS_METERS / METERS_PER_DEGREE

        val links = itsClient.getTrafficInfo(
            minX = stationWgs84.longitude - searchRadiusDegrees,
            maxX = stationWgs84.longitude + searchRadiusDegrees,
            minY = stationWgs84.latitude - searchRadiusDegrees,
            maxY = stationWgs84.latitude + searchRadiusDegrees
        )

        val matchResult = nearestLinkFinder.findNearestLinkId(
            station.katecX, station.katecY, userKatecX, userKatecY
        )

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
                log.debug("주유소 [{}] 주변 링크 매칭 실패, fallback", station.name)
            }
        }

        // fallback: 영역 내 최저 속도
        val validSpeeds = links.map { it.speedAsDouble() }.filter { it > 0 }
        if (validSpeeds.isEmpty()) return 0.0
        return validSpeeds.min()
    }
}
