package com.kumoh.lbs.service

import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetClient.SortType
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.GasStation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GasStationFinder(
    private val opinetClient: OpinetClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findNearby(center: Coordinate, radius: Int, fuelType: String): List<GasStation> {
        val stations = opinetClient.searchByRadius(center, radius, fuelType, SortType.PRICE)
        log.info("주변 주유소 검색: {} 건, radius={}", stations.size, radius)
        return stations
    }
}
