package com.kumoh.lbs.controller

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import com.kumoh.lbs.service.GasStationRecommender
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/gas-stations")
class GasStationController(
    private val gasStationRecommender: GasStationRecommender
) {

    @GetMapping("/best")
    fun findBest(
        userLocation: Coordinate,
        @RequestParam @Positive @Max(5000) radius: Int,
        @RequestParam fuelType: FuelType,
        @RequestParam @Positive fuelAmount: Double,
        @RequestParam @Positive fuelEfficiency: Double,
        @RequestParam @Min(1) @Max(5) limit: Int
    ): List<GasStationResponse> =
        gasStationRecommender.recommend(userLocation, radius, fuelType, fuelAmount, fuelEfficiency, limit)
            .map { GasStationResponse.from(it) }
}
