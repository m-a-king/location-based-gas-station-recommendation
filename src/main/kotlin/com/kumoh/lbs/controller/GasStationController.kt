package com.kumoh.lbs.controller

import com.kumoh.lbs.domain.Coordinate
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
        coordinate: Coordinate,
        @RequestParam radius: Int,
        @RequestParam fuelType: String,
        @RequestParam limit: Int
    ): List<ScoredGasStation> =
        gasStationFinder.findBest(coordinate, radius, fuelType, limit)
}
