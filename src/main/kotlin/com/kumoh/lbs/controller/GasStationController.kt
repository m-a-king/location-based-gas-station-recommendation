package com.kumoh.lbs.controller

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.ScoredGasStation
import com.kumoh.lbs.service.GasStationRecommender
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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
        @RequestParam radius: Int,
        @RequestParam fuelType: String,
        @RequestParam @Min(1) @Max(5) limit: Int
    ): List<ScoredGasStation> =
        gasStationRecommender.recommend(userLocation, radius, fuelType, limit)
}
