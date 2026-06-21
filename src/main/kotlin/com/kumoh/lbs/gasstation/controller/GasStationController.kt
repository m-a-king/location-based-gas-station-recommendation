package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.auth.CurrentUserProvider
import com.kumoh.lbs.geo.Coordinate
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.service.GasStationRadiusRecommender
import com.kumoh.lbs.gasstation.service.GasStationRouteRecommender
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Positive
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "주유소 추천", description = "반경/경로 기반 최적 주유소 추천 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/gas-stations/recommendations")
class GasStationController(
    private val radiusRecommender: GasStationRadiusRecommender,
    private val routeRecommender: GasStationRouteRecommender,
    private val currentUserProvider: CurrentUserProvider
) {

    @Operation(summary = "반경 기반 추천", description = "현재 위치 반경 내 주유소를 가격+이동비용 기준으로 추천합니다. 유종·연비는 로그인 사용자의 차량 프로필에서 가져옵니다.")
    @GetMapping("/radius")
    fun findByRadius(
        @AuthenticationPrincipal principal: Any,
        @Parameter(description = "위도 (WGS84)", example = "37.5") @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") latitude: Double,
        @Parameter(description = "경도 (WGS84)", example = "127.0") @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") longitude: Double,
        @Parameter(description = "탐색 반경 (m, 최대 5000)", example = "3000") @RequestParam @Positive @Max(5000) radius: Int,
        @Parameter(description = "주유량 (L, 미입력 시 기본 50)", example = "50.0") @RequestParam(defaultValue = "50.0") @Positive refuelLiters: Double,
        @Parameter(description = "추천 개수 (최대 3)", example = "3") @RequestParam @Positive @Max(3) limit: Int
    ): List<GasStationResponse> {
        val (fuelType, fuelEfficiency) = resolveProfile(principal)
        val userLocation = Coordinate.fromWgs84(Coordinate.Wgs84(latitude, longitude))

        val scored = radiusRecommender.recommend(userLocation, radius, fuelType, refuelLiters, fuelEfficiency, limit)
        return GasStationResponse.fromList(scored)
    }

    @Operation(summary = "경로 기반 추천", description = "출발지→도착지 경로 상에서 경유 시 총 비용이 최소화되는 주유소를 추천합니다. 유종·연비는 로그인 사용자의 차량 프로필에서 가져옵니다.")
    @GetMapping("/route")
    fun findByRoute(
        @AuthenticationPrincipal principal: Any,
        @Parameter(description = "출발지 위도 (WGS84)", example = "37.5") @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") originLatitude: Double,
        @Parameter(description = "출발지 경도 (WGS84)", example = "127.0") @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") originLongitude: Double,
        @Parameter(description = "도착지 위도 (WGS84)", example = "35.1") @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") destinationLatitude: Double,
        @Parameter(description = "도착지 경도 (WGS84)", example = "129.0") @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") destinationLongitude: Double,
        @Parameter(description = "주유량 (L, 미입력 시 기본 50)", example = "50.0") @RequestParam(defaultValue = "50.0") @Positive refuelLiters: Double,
        @Parameter(description = "추천 개수 (최대 3)", example = "3") @RequestParam @Positive @Max(3) limit: Int
    ): List<GasStationResponse> {
        val (fuelType, fuelEfficiency) = resolveProfile(principal)
        val origin = Coordinate.fromWgs84(Coordinate.Wgs84(originLatitude, originLongitude))
        val destination = Coordinate.fromWgs84(Coordinate.Wgs84(destinationLatitude, destinationLongitude))

        val result = routeRecommender.recommend(origin, destination, fuelType, refuelLiters, fuelEfficiency, limit)
        return GasStationResponse.fromList(result.scored, result.savingsBaselinePrice)
    }

    /**
     * 로그인 사용자의 차량 프로필에서 유종·연비를 읽되, 미입력 시 기본값으로 대체한다.
     * 프로필 입력을 강제하지 않아 누구나 바로 추천을 받을 수 있다(유연성 우선).
     * - 유종 미입력 → 휘발유(가장 보편적이며 FavoriteService 폴백과 일치). 경유·LPG 운전자는 입력 전까지 휘발유 가격 기준.
     * - 연비 미입력 → 10km/L.
     */
    private fun resolveProfile(principal: Any): Pair<FuelType, Double> {
        val user = currentUserProvider.resolve(principal)
        val fuelType = user.fuelType ?: DEFAULT_FUEL_TYPE
        val fuelEfficiency = user.fuelEfficiency ?: DEFAULT_FUEL_EFFICIENCY
        return fuelType to fuelEfficiency
    }

    companion object {
        private val DEFAULT_FUEL_TYPE = FuelType.GASOLINE
        private const val DEFAULT_FUEL_EFFICIENCY = 10.0
    }
}
