package com.kumoh.lbs.controller

import com.kumoh.lbs.domain.ScoredGasStation
import com.kumoh.lbs.service.GasStationFinder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/gas-stations")
class GasStationController(
    private val gasStationFinder: GasStationFinder
) {

    @GetMapping("/best")
    fun findBest(
        @RequestParam katecX: Double,
        @RequestParam katecY: Double,
        @RequestParam(defaultValue = "3000") radius: Int,
        @RequestParam(defaultValue = "B027") fuelType: String,
        @RequestParam(defaultValue = "5") limit: Int
    ): List<ScoredGasStation> =
        gasStationFinder.findBest(katecX, katecY, radius, fuelType, limit)
}
