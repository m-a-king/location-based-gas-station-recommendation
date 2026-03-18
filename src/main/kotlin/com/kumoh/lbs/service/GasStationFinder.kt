package com.kumoh.lbs.service

import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetClient.SortType
import com.kumoh.lbs.domain.ScoredGasStation
import com.kumoh.lbs.domain.strategy.ScoringStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GasStationFinder(
    private val opinetClient: OpinetClient,
    private val scoringStrategy: ScoringStrategy,
    private val frontRoadSpeedFinder: FrontRoadSpeedFinder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findBest(
        katecX: Double,
        katecY: Double,
        radius: Int,
        fuelType: String,
        limit: Int
    ): List<ScoredGasStation> {
        require(limit in 1..5)
        val stations = opinetClient.searchByRadius(katecX, katecY, radius, fuelType, SortType.PRICE)
        log.info("Opinet API 응답: {} 건, katecX={}, katecY={}, radius={}", stations.size, katecX, katecY, radius)

        return stations
            .map {
                ScoredGasStation(
                    it,
                    scoringStrategy.score(it),
                    frontRoadSpeedFinder.findSpeed(it.katecX, it.katecY)
                )
            }
            .sortedBy { it.score }
            .take(limit)
    }
}
