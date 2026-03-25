package com.kumoh.lbs.controller

import com.fasterxml.jackson.annotation.JsonInclude
import com.kumoh.lbs.domain.ScoredGasStation

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GasStationResponse(
    val opinetStationId: String,
    val name: String,
    val brand: String,
    val latitude: Double,
    val longitude: Double,
    val price: Int,
    val distance: Double,
    val score: Double,
    val frontRoadSpeed: Double,
    val detourDistance: Double? = null
) {
    companion object {
        fun from(scored: ScoredGasStation) = GasStationResponse(
            opinetStationId = scored.station.id,
            name = scored.station.name,
            brand = scored.station.brand,
            latitude = scored.station.location.wgs84.latitude,
            longitude = scored.station.location.wgs84.longitude,
            price = scored.station.price,
            distance = scored.station.distance,
            score = scored.score,
            frontRoadSpeed = scored.frontRoadSpeed,
            detourDistance = scored.detourDistance
        )
    }
}
