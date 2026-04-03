package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.Route
import com.kumoh.lbs.gasstation.domain.ScoredGasStation
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import com.kumoh.lbs.geo.BoundingBox
import com.kumoh.lbs.geo.Coordinate
import com.kumoh.lbs.geo.GeoUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class GasStationRouteRecommender(
    private val kakaoDirectionsClient: KakaoDirectionsClient,
    private val gasStationRepository: GasStationRepository,
    private val gasStationPriceRepository: GasStationPriceRepository
) {

    companion object {
        private const val BUFFER_RADIUS_METERS = 2000.0
        private const val PRELIMINARY_FILTER_MULTIPLIER = 3
    }

    fun recommend(
        origin: Coordinate,
        destination: Coordinate,
        fuelType: FuelType,
        refuelLiters: Double,
        fuelEfficiency: Double,
        limit: Int
    ): List<ScoredGasStation> {
        val baseRoute = fetchBaseRoute(origin, destination)
        val candidates = gatherCandidates(baseRoute, fuelType, refuelLiters, fuelEfficiency)
        val rankedByEstimatedDetour = rankByEstimatedDetour(candidates, limit)
        return rankByActualDetour(
            rankedByEstimatedDetour,
            baseRoute,
            origin,
            destination,
            refuelLiters,
            fuelEfficiency,
            limit
        )
    }

    private fun fetchBaseRoute(origin: Coordinate, destination: Coordinate): Route =
        kakaoDirectionsClient.searchRoute(origin, destination)
            ?: throw IllegalStateException("경로를 찾을 수 없습니다.")

    private fun gatherCandidates(
        baseRoute: Route,
        fuelType: FuelType,
        refuelLiters: Double,
        fuelEfficiency: Double
    ): List<ScoredGasStation> {
        val mbrBounds = BoundingBox.aroundPolyline(baseRoute.polyline, BUFFER_RADIUS_METERS)
        val corridorStations = gasStationRepository.findInBounds(mbrBounds)
            .map { it to GeoUtils.calculateMinDistanceToPolyline(it.coordinate, baseRoute.polyline) }
            .filter { (_, dist) -> dist <= BUFFER_RADIUS_METERS }
            .also { logger.info { "경로 corridor 내 주유소: ${it.size}건 (BUFFER=${BUFFER_RADIUS_METERS.toInt()}m)" } }

        val prices = gasStationPriceRepository
            .findAllByIdStationIdInAndIdFuelType(corridorStations.map { (station, _) -> station.id }, fuelType)
            .associate { it.id.stationId to it.price }

        val missingIds = corridorStations.map { (station, _) -> station.id } - prices.keys
        if (missingIds.isNotEmpty()) logger.warn { "가격 정보 없음 (제외): stationIds=$missingIds, fuelType=$fuelType" }

        return corridorStations
            .filter { (station, _) -> station.id !in missingIds }
            .map { (station, distanceFromRoute) ->
                val estimatedRoundTripDetour = distanceFromRoute * 2
                ScoredGasStation.of(station, prices.getValue(station.id), estimatedRoundTripDetour, refuelLiters, fuelEfficiency)
            }
    }

    private fun rankByEstimatedDetour(
        candidates: List<ScoredGasStation>,
        limit: Int
    ): List<ScoredGasStation> =
        candidates
            .sortedBy { it.score }
            .take(limit * PRELIMINARY_FILTER_MULTIPLIER)
            .also { logger.info { "1차 필터 통과: ${it.size}건" } }

    private fun rankByActualDetour(
        rankedByEstimatedDetour: List<ScoredGasStation>,
        baseRoute: Route,
        origin: Coordinate,
        destination: Coordinate,
        refuelLiters: Double,
        fuelEfficiency: Double,
        limit: Int
    ): List<ScoredGasStation> =
        rankedByEstimatedDetour
            .map { candidate ->
                val routeViaStation =
                    kakaoDirectionsClient.searchRouteViaWaypoint(origin, destination, candidate.station.coordinate)
                        ?: throw IllegalStateException("경유 경로 조회 실패: stationId=${candidate.station.id}")
                val actualDetour =
                    (routeViaStation.distanceMeters - baseRoute.distanceMeters).coerceAtLeast(0).toDouble()
                ScoredGasStation.of(
                    candidate.station,
                    candidate.price,
                    actualDetour,
                    refuelLiters,
                    fuelEfficiency,
                    isActualDetour = true
                )
            }
            .sortedBy { it.score }
            .take(limit)
}
