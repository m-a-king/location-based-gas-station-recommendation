package com.kumoh.lbs.service

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.GasStation
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
            .map {
                ScoredGasStation(
                    it,
                    score(it),
                    trafficSpeedFinder.findAt(it.location)
                )
            }
            .sortedBy { it.score }
            .take(limit)
    }

    private fun score(station: GasStation): Double {
        val priceScore = station.price.toDouble()
        val distanceScore = station.distance * DISTANCE_WEIGHT
        return priceScore + distanceScore
    }

    companion object {
        private const val DISTANCE_WEIGHT = 0.5
    }
}
