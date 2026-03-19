package com.kumoh.lbs.controller

import com.kumoh.lbs.domain.ScoredGasStation

data class GasStationResponse(
    val id: String,
    val name: String,
    val brand: String,
    val latitude: Double,
    val longitude: Double,
    val price: Int,
    val distance: Double,
    val score: Double,
    val trafficSpeed: Double
) {
    companion object {
        fun from(scored: ScoredGasStation) = GasStationResponse(
            id = scored.station.id,
            name = scored.station.name,
            brand = scored.station.brand,
            latitude = scored.station.location.wgs84.latitude,
            longitude = scored.station.location.wgs84.longitude,
            price = scored.station.price,
            distance = scored.station.distance,
            score = scored.score,
            trafficSpeed = scored.trafficSpeed
        )
    }
}
