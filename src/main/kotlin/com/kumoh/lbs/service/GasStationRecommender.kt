package com.kumoh.lbs.service

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
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
        fuelType: FuelType,
        fuelAmount: Double,
        fuelEfficiency: Double,
        limit: Int
    ): List<ScoredGasStation> {
        val stations = gasStationFinder.findNearby(userLocation, radius, fuelType)

        val topCandidates = stations
            .map { ScoredGasStation.of(it, fuelAmount, fuelEfficiency) }
            .sortedBy { it.score }
            .take(limit)

        return topCandidates.map {
            it.withTrafficSpeed(trafficSpeedFinder.findAt(it.station.location))
        }
    }
}
