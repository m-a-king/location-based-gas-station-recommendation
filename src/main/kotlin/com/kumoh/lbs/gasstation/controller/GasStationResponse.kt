package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.gasstation.domain.ScoredGasStation

data class GasStationResponse(
    val opinetStationId: String,
    val name: String,
    val brand: String,
    val latitude: Double,
    val longitude: Double,
    val price: Int,
    val distance: Double,
    val durationSeconds: Int,
    val score: Double,
    val isActualDetour: Boolean,
    val estimatedFuelCost: Int,
    val estimatedDetourCost: Int,
    val estimatedSavings: Int
) {
    companion object {
        fun fromList(scoredList: List<ScoredGasStation>): List<GasStationResponse> {
            if (scoredList.isEmpty()) return emptyList()
            val maxPrice = scoredList.maxOf { it.price }
            return scoredList.map { from(it, maxPrice) }
        }

        private fun from(scored: ScoredGasStation, maxPriceInCandidates: Int) = GasStationResponse(
            opinetStationId = scored.station.id,
            name = scored.station.name,
            brand = scored.station.brand,
            latitude = scored.station.latitude,
            longitude = scored.station.longitude,
            price = scored.price,
            distance = scored.detourDistanceMeters,
            durationSeconds = scored.detourSeconds,
            score = scored.score,
            isActualDetour = scored.isActualDetour,
            estimatedFuelCost = scored.fuelCost.toInt(),
            estimatedDetourCost = (scored.detourFuelCost + scored.detourTimeCost).toInt(),
            estimatedSavings = ((maxPriceInCandidates - scored.price) * scored.refuelLiters).toInt()
        )
    }
}
