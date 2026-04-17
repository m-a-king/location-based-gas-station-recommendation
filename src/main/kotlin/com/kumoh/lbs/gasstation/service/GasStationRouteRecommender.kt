package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.CandidateCascadePolicy
import com.kumoh.lbs.gasstation.domain.CandidateSelectionStage
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.RecommendResult
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
        private const val DETOUR_RATIO = 0.3
        private const val MAX_DETOUR_METERS = 10_000.0
        private const val MBR_BUFFER_MULTIPLIER = 2.0

        private fun maxDetourMeters(baseDistanceMeters: Int): Double =
            minOf(baseDistanceMeters * DETOUR_RATIO, MAX_DETOUR_METERS)

        private fun mbrBufferMeters(baseDistanceMeters: Int): Double =
            maxDetourMeters(baseDistanceMeters) * MBR_BUFFER_MULTIPLIER
    }

    fun recommend(
        origin: Coordinate,
        destination: Coordinate,
        fuelType: FuelType,
        refuelLiters: Double,
        fuelEfficiency: Double,
        limit: Int
    ): RecommendResult {
        val baseRoute = fetchBaseRoute(origin = origin, destination = destination)
        val candidates = gatherCandidates(baseRoute = baseRoute, fuelType = fuelType)
        if (candidates.isEmpty()) return RecommendResult.empty()
        val maxPrice = candidates.maxOf { (_, price) -> price }
        val scored = rankByPriceLowerBound(
            candidates = candidates,
            baseRoute = baseRoute,
            origin = origin,
            destination = destination,
            refuelLiters = refuelLiters,
            fuelEfficiency = fuelEfficiency,
            limit = limit
        )
        return RecommendResult(scored = scored, maxPriceInCandidates = maxPrice)
    }

    private fun fetchBaseRoute(origin: Coordinate, destination: Coordinate): Route {
        val route = kakaoDirectionsClient.searchRoute(origin = origin, destination = destination)
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
        val collected = collectWithinMbr(baseRoute = baseRoute)
        val narrowed = collected.filterByTightCorridor(
            threshold = CandidateCascadePolicy.N_THRESHOLD,
            polyline = baseRoute.polyline,
            corridorMeters = CandidateCascadePolicy.TIGHT_CORRIDOR_METERS
        )
        if (narrowed.stage != collected.stage) {
            logger.info { "[TIGHT_CORRIDOR] corridor=${CandidateCascadePolicy.TIGHT_CORRIDOR_METERS.toInt()}m, 주유소=${narrowed.stations.size}건" }
        }

        val priced = attachPrices(pool = narrowed, fuelType = fuelType)
        val capped = priced.filterByPriceCap(hardCap = CandidateCascadePolicy.HARD_CAP)
        if (capped.stage != priced.stage) {
            logger.info { "[PRICE_CAPPED] 상한=${CandidateCascadePolicy.HARD_CAP}개 적용" }
        }

        logger.info { "후보 수집 단계=${capped.stage}, 최종 후보=${capped.priced.size}건" }
        return capped.priced
    }

    private fun collectWithinMbr(baseRoute: Route): CandidatePool {
        val bufferMeters = mbrBufferMeters(baseDistanceMeters = baseRoute.distanceMeters)
        val bounds = BoundingBox.aroundPolyline(polyline = baseRoute.polyline, bufferMeters = bufferMeters)
        val stations = gasStationRepository.findInBounds(bounds)
        logger.info { "[POLYLINE_MBR] MBR buffer=${bufferMeters.toInt()}m, 주유소=${stations.size}건" }
        return CandidatePool(stations = stations, stage = CandidateSelectionStage.POLYLINE_MBR)
    }

    private fun attachPrices(pool: CandidatePool, fuelType: FuelType): PricedCandidatePool {
        val prices = gasStationPriceRepository
            .findAllByIdStationIdInAndIdFuelType(stationIds = pool.stations.map { it.id }, fuelType = fuelType)
            .associate { it.id.stationId to it.price }

        val missingIds = pool.stations.map { it.id } - prices.keys
        if (missingIds.isNotEmpty()) logger.warn { "가격 정보 없음 (제외): stationIds=$missingIds, fuelType=$fuelType" }

        val priced = pool.stations
            .filter { it.id !in missingIds }
            .map { it to prices.getValue(it.id) }
            .sortedBy { (_, price) -> price }
        return PricedCandidatePool(priced = priced, stage = pool.stage)
    }

    private data class CandidatePool(
        val stations: List<GasStation>,
        val stage: CandidateSelectionStage
    ) {
        fun filterByTightCorridor(
            threshold: Int,
            polyline: List<Coordinate>,
            corridorMeters: Double
        ): CandidatePool {
            if (stations.size <= threshold) return this
            val narrowed = stations.filter {
                GeoUtils.calculateMinDistanceToPolyline(it.coordinate, polyline) <= corridorMeters
            }
            return copy(stations = narrowed, stage = CandidateSelectionStage.TIGHT_CORRIDOR)
        }
    }

    private data class PricedCandidatePool(
        val priced: List<Pair<GasStation, Int>>,
        val stage: CandidateSelectionStage
    ) {
        fun filterByPriceCap(hardCap: Int): PricedCandidatePool {
            if (priced.size <= hardCap) return this
            return copy(priced = priced.take(hardCap), stage = CandidateSelectionStage.PRICE_CAPPED)
        }
    }

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
        val detourLimit = maxDetourMeters(baseDistanceMeters = baseRoute.distanceMeters)

        for ((index, candidate) in candidates.withIndex()) {
            val (station, price) = candidate
            if (price * refuelLiters > kthBestScore) {
                logger.info { "price lower bound 초과로 탐색 종료: ${candidates.size - index}건 pruning" }
                break
            }

            val routeViaStation = kakaoDirectionsClient.searchRouteViaWaypoint(
                origin = origin, destination = destination, waypoint = station.coordinate
            )
            if (routeViaStation == null) {
                logger.warn { "경유 경로 조회 실패, 건너뜀: stationId=${station.id}" }
                continue
            }

            val actualDetour = (routeViaStation.distanceMeters - baseRoute.distanceMeters).coerceAtLeast(0).toDouble()
            if (actualDetour > detourLimit) {
                logger.info { "우회 상한 초과 제외: stationId=${station.id}, detour=${actualDetour.toInt()}m" }
                continue
            }
            val actualDetourSeconds = (routeViaStation.durationSeconds - baseRoute.durationSeconds).coerceAtLeast(0)
            val scored = ScoredGasStation(
                station = station,
                price = price,
                detourDistanceMeters = actualDetour,
                detourSeconds = actualDetourSeconds,
                refuelLiters = refuelLiters,
                fuelEfficiency = fuelEfficiency,
                isActualDetour = true
            )

            results.add(scored)
            if (results.size >= limit) {
                kthBestScore = results.sortedBy { it.score }[limit - 1].score
            }
        }

        logger.info { "Kakao API 호출: ${results.size}회" }
        return results.sortedBy { it.score }.take(limit)
    }
}
