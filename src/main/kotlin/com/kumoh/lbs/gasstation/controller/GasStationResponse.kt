package com.kumoh.lbs.gasstation.controller

import com.fasterxml.jackson.annotation.JsonInclude
import com.kumoh.lbs.gasstation.domain.ScoredGasStation

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
    val detourDistance: Double? = null
) {
    companion object {
        fun from(scored: ScoredGasStation) = GasStationResponse(
            opinetStationId = scored.station.id,
            name = scored.station.name,
            brand = scored.station.brand,
            latitude = scored.station.latitude,
            longitude = scored.station.longitude,
            price = scored.price,
            distance = scored.distance,
            score = scored.score,
            detourDistance = scored.detourDistance
        )
    }
}
