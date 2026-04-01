package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.common.client.OpinetClient
import com.kumoh.lbs.common.client.OpinetClient.SortType
import com.kumoh.lbs.common.domain.Coordinate
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.ScoredGasStation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class GasStationRadiusRecommender(
    private val opinetClient: OpinetClient
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
            .map { ScoredGasStation.of(it.toGasStation(), it.price, it.distance, refuelLiters, fuelEfficiency) }
            .sortedBy { it.score }
            .take(limit)

        return topCandidates
    }
}
