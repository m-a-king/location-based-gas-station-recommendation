package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
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
        val candidates = gatherCandidates(baseRoute, fuelType)
        return rankByPriceLowerBound(candidates, baseRoute, origin, destination, refuelLiters, fuelEfficiency, limit)
    }

    private fun fetchBaseRoute(origin: Coordinate, destination: Coordinate): Route {
        val route = kakaoDirectionsClient.searchRoute(origin, destination)
            ?: throw IllegalStateException("경로를 찾을 수 없습니다.")
        check(route.polyline.size >= 2) {
            "유효하지 않은 경로 polyline: ${route.polyline.size}점 (최소 2점 필요)"
        }
        return route
    }

    private fun gatherCandidates(
        baseRoute: Route,
        fuelType: FuelType
    ): List<Pair<GasStation, Int>> {
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
            .map { (station, _) -> station to prices.getValue(station.id) }
            .sortedBy { (_, price) -> price }
    }

    // score = price × refuelLiters + (detourKm / fuelEfficiency) × price
    // detour ≥ 0 이므로 price × refuelLiters 는 score의 수학적 하한(lower bound)
    // → 가격 오름차순 탐색 중, top-limit 결과가 확보된 후
    //   price × refuelLiters > 현재 k번째 최선 score 이면 이후 모든 후보 pruning 가능
    private fun rankByPriceLowerBound(
        candidates: List<Pair<GasStation, Int>>,
        baseRoute: Route,
        origin: Coordinate,
        destination: Coordinate,
        refuelLiters: Double,
        fuelEfficiency: Double,
        limit: Int
    ): List<ScoredGasStation> {
        val results = mutableListOf<ScoredGasStation>()
        var kthBestScore = Double.MAX_VALUE

        for ((index, candidate) in candidates.withIndex()) {
            val (station, price) = candidate
            if (price * refuelLiters > kthBestScore) {
                logger.info { "price lower bound 초과로 탐색 종료: ${candidates.size - index}건 pruning" }
                break
            }

            val routeViaStation = kakaoDirectionsClient.searchRouteViaWaypoint(origin, destination, station.coordinate)
                ?: throw IllegalStateException("경유 경로 조회 실패: stationId=${station.id}")

            val actualDetour = (routeViaStation.distanceMeters - baseRoute.distanceMeters).coerceAtLeast(0).toDouble()
            val actualDetourSeconds = (routeViaStation.durationSeconds - baseRoute.durationSeconds).coerceAtLeast(0)
            val scored = ScoredGasStation.of(station, price, actualDetour, refuelLiters, fuelEfficiency, actualDetourSeconds, isActualDetour = true)

            results.add(scored)
            if (results.size >= limit) {
                kthBestScore = results.sortedBy { it.score }[limit - 1].score
            }
        }

        logger.info { "Kakao API 호출: ${results.size}회" }
        return results.sortedBy { it.score }.take(limit)
    }
}
