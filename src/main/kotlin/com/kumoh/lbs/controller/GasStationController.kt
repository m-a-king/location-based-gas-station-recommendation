package com.kumoh.lbs.controller

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import com.kumoh.lbs.service.GasStationRadiusRecommender
import com.kumoh.lbs.service.GasStationRouteRecommender
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
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
        @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") latitude: Double,
        @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") longitude: Double,
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
        @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") originLatitude: Double,
        @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") originLongitude: Double,
        @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") destinationLatitude: Double,
        @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") destinationLongitude: Double,
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
