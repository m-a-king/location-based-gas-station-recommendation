package com.kumoh.lbs.service

import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetClient.SortType
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import com.kumoh.lbs.domain.ScoredGasStation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class GasStationRadiusRecommender(
    private val opinetClient: OpinetClient,
    private val trafficSpeedFinder: TrafficSpeedFinder
) {

    fun recommend(
        userLocation: Coordinate,
        radius: Int,
        fuelType: FuelType,
        refuelLiters: Double,
        fuelEfficiency: Double,
        limit: Int
    ): List<ScoredGasStation> {
        val stations = opinetClient.searchByRadius(userLocation, radius, fuelType, SortType.PRICE)
        logger.info { "주변 주유소 검색: ${stations.size}건, radius=$radius" }

        val topCandidates = stations
            .map { ScoredGasStation.of(it, refuelLiters, fuelEfficiency) }
            .sortedBy { it.score }
            .take(limit)

        return topCandidates.map {
            it.withFrontRoadSpeed(trafficSpeedFinder.findAt(it.station.location))
        }
    }
}
