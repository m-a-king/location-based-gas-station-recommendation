package com.kumoh.lbs.service

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.ScoredGasStation
import org.springframework.stereotype.Service

@Service
class GasStationRecommender(
    private val gasStationFinder: GasStationFinder,
    private val trafficSpeedFinder: TrafficSpeedFinder
) {

    fun recommend(
        userLocation: Coordinate,
        radius: Int,
        fuelType: String,
        limit: Int
    ): List<ScoredGasStation> {
        val stations = gasStationFinder.findNearby(userLocation, radius, fuelType)

        return stations
            .map { ScoredGasStation.of(it, trafficSpeedFinder.findAt(it.location)) }
            .sortedBy { it.score }
            .take(limit)
    }
}
