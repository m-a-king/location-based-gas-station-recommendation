package com.kumoh.lbs.service

import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetClient.SortType
import com.kumoh.lbs.domain.Coordinate
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
        location: Coordinate,
        radius: Int,
        fuelType: String,
        limit: Int
    ): List<ScoredGasStation> {
        require(limit in 1..5)
        val stations = opinetClient.searchByRadius(location.katec.x, location.katec.y, radius, fuelType, SortType.PRICE)
        log.info("Opinet API 응답: {} 건, radius={}", stations.size, radius)

        return stations
            .map {
                ScoredGasStation(
                    it,
                    scoringStrategy.score(it),
                    frontRoadSpeedFinder.findSpeed(it.coordinate)
                )
            }
            .sortedBy { it.score }
            .take(limit)
    }
}
