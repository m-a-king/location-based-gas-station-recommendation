package com.kumoh.lbs.service

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.ScoredGasStation
import com.kumoh.lbs.domain.strategy.ScoringStrategy
import org.springframework.stereotype.Service

@Service
class GasStationRecommender(
    private val gasStationFinder: GasStationFinder,
    private val scoringStrategy: ScoringStrategy,
    private val frontRoadSpeedFinder: FrontRoadSpeedFinder
) {

    fun recommend(
        userLocation: Coordinate,
        radius: Int,
        fuelType: String,
        limit: Int
    ): List<ScoredGasStation> {
        val stations = gasStationFinder.findNearby(userLocation, radius, fuelType)

        return stations
            .map {
                ScoredGasStation(
                    it,
                    scoringStrategy.score(it),
                    frontRoadSpeedFinder.findSpeed(it.location)
                )
            }
            .sortedBy { it.score }
            .take(limit)
    }
}
