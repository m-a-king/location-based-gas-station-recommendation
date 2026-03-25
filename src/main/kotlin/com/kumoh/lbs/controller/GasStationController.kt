package com.kumoh.lbs.controller

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import com.kumoh.lbs.service.GasStationRadiusRecommender
import com.kumoh.lbs.service.GasStationRouteRecommender
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Positive
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/gas-stations/recommendations")
class GasStationController(
    private val radiusRecommender: GasStationRadiusRecommender,
    private val routeRecommender: GasStationRouteRecommender
) {

    @GetMapping("/radius")
    fun findByRadius(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam @Positive @Max(5000) radius: Int,
        @RequestParam fuelType: FuelType,
        @RequestParam @Positive refuelLiters: Double,
        @RequestParam @Positive fuelEfficiency: Double,
        @RequestParam @Positive @Max(5) limit: Int
    ): List<GasStationResponse> {
        val userLocation = Coordinate.fromWgs84(Coordinate.Wgs84(latitude, longitude))

        return radiusRecommender.recommend(userLocation, radius, fuelType, refuelLiters, fuelEfficiency, limit)
            .map { GasStationResponse.from(it) }
    }

    @GetMapping("/route")
    fun findByRoute(
        @RequestParam originLatitude: Double,
        @RequestParam originLongitude: Double,
        @RequestParam destinationLatitude: Double,
        @RequestParam destinationLongitude: Double,
        @RequestParam fuelType: FuelType,
        @RequestParam @Positive refuelLiters: Double,
        @RequestParam @Positive fuelEfficiency: Double,
        @RequestParam @Positive @Max(5) limit: Int
    ): List<GasStationResponse> {
        val origin = Coordinate.fromWgs84(Coordinate.Wgs84(originLatitude, originLongitude))
        val destination = Coordinate.fromWgs84(Coordinate.Wgs84(destinationLatitude, destinationLongitude))

        return routeRecommender.recommend(origin, destination, fuelType, refuelLiters, fuelEfficiency, limit)
            .map { GasStationResponse.from(it) }
    }
}
