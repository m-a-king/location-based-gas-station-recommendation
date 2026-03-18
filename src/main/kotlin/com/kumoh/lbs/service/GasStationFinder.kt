package com.kumoh.lbs.service

import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetClient.SortType
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.GasStation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class GasStationFinder(
    private val opinetClient: OpinetClient
) {

    fun findNearby(center: Coordinate, radius: Int, fuelType: String): List<GasStation> {
        val stations = opinetClient.searchByRadius(center, radius, fuelType, SortType.PRICE)
        logger.info { "주변 주유소 검색: ${stations.size} 건, radius=$radius" }
        return stations
    }
}
