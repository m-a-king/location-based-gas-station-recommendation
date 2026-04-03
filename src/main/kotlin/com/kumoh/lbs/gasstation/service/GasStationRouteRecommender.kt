package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.NearbyStation
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
        val corridorCandidates = findCandidatesAlongRoute(baseRoute, fuelType)
        val preliminaryRanking =
            rankByEstimatedDetour(corridorCandidates, baseRoute, refuelLiters, fuelEfficiency, limit)
        return refineByActualDetour(
            preliminaryRanking,
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

    private fun findCandidatesAlongRoute(baseRoute: Route, fuelType: FuelType): List<NearbyStation> {
        val mbrBounds = BoundingBox.aroundPolyline(baseRoute.polyline, BUFFER_RADIUS_METERS)
        val stationsInMbr = gasStationRepository.findInBounds(mbrBounds)
        val corridorStations = stationsInMbr.filter { station ->
            val distanceFromRoute = GeoUtils.calculateMinDistanceToPolyline(station.coordinate, baseRoute.polyline)
            distanceFromRoute <= BUFFER_RADIUS_METERS
        }
        val stationIdToPrice = gasStationPriceRepository
            .findAllByIdStationIdInAndIdFuelType(corridorStations.map { it.id }, fuelType)
            .associate { it.id.stationId to it.price }

        return corridorStations.mapNotNull { station ->
            val price = stationIdToPrice[station.id] ?: return@mapNotNull null
            NearbyStation(station, price, distanceMeters = 0.0)
        }.also { logger.info { "경로 corridor 내 주유소: ${it.size}건 (BUFFER=${BUFFER_RADIUS_METERS.toInt()}m)" } }
    }

    private fun rankByEstimatedDetour(
        corridorCandidates: List<NearbyStation>,
        baseRoute: Route,
        refuelLiters: Double,
        fuelEfficiency: Double,
        limit: Int
    ): List<ScoredGasStation> =
        corridorCandidates
            .map { candidate ->
                val estimatedDetour =
                    GeoUtils.calculateMinDistanceToPolyline(candidate.station.coordinate, baseRoute.polyline) * 2
                ScoredGasStation.ofWithDetour(
                    candidate.station,
                    candidate.price,
                    estimatedDetour,
                    refuelLiters,
                    fuelEfficiency,
                    estimatedDetour
                )
            }
            .sortedBy { it.score }
            .take(limit * PRELIMINARY_FILTER_MULTIPLIER)
            .also { logger.info { "1차 필터 통과: ${it.size}건" } }

    private fun refineByActualDetour(
        preliminaryRanking: List<ScoredGasStation>,
        baseRoute: Route,
        origin: Coordinate,
        destination: Coordinate,
        refuelLiters: Double,
        fuelEfficiency: Double,
        limit: Int
    ): List<ScoredGasStation> =
        preliminaryRanking
            .map { candidate ->
                val routeViaStation =
                    kakaoDirectionsClient.searchRouteViaWaypoint(origin, destination, candidate.station.coordinate)
                if (routeViaStation == null) {
                    logger.debug { "경유 경로 조회 실패: ${candidate.station.name}, 1차 점수 유지" }
                    return@map candidate
                }
                val actualDetour =
                    (routeViaStation.distanceMeters - baseRoute.distanceMeters).coerceAtLeast(0).toDouble()
                ScoredGasStation.ofWithDetour(
                    candidate.station,
                    candidate.price,
                    candidate.distance,
                    refuelLiters,
                    fuelEfficiency,
                    actualDetour
                )
            }
            .sortedBy { it.score }
            .take(limit)
}
