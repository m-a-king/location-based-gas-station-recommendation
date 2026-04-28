package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.CandidateCascadePolicy
import com.kumoh.lbs.gasstation.domain.CandidateSelectionStage
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.PricedGasStation
import com.kumoh.lbs.gasstation.domain.RecommendResult
import com.kumoh.lbs.gasstation.domain.Route
import com.kumoh.lbs.gasstation.domain.ScoredGasStation
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import com.kumoh.lbs.geo.BoundingBox
import com.kumoh.lbs.geo.Coordinate
import com.kumoh.lbs.geo.GeoUtils
import com.kumoh.lbs.infra.RouteRecommenderProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

private val logger = KotlinLogging.logger {}

@Service
class GasStationRouteRecommender(
    private val kakaoDirectionsClient: KakaoDirectionsClient,
    private val gasStationRepository: GasStationRepository,
    private val gasStationPriceRepository: GasStationPriceRepository,
    private val routeExecutor: Executor,
    private val properties: RouteRecommenderProperties
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
        val baselinePrice = candidates.maxOf { it.price }
        val scored = rankByPriceLowerBound(
            candidates = candidates,
            baseRoute = baseRoute,
            origin = origin,
            destination = destination,
            refuelLiters = refuelLiters,
            fuelEfficiency = fuelEfficiency,
            limit = limit
        )
        return RecommendResult(scored = scored, savingsBaselinePrice = baselinePrice)
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
    ): List<PricedGasStation> {
        val collected = collectWithinMbr(baseRoute = baseRoute)
        val priced = attachPrices(pool = collected, fuelType = fuelType)
        val ceilinged = priced.filterByRoutePriceCeiling(
            threshold = CandidateCascadePolicy.N_THRESHOLD,
            polyline = baseRoute.polyline,
            onRouteRadiusMeters = CandidateCascadePolicy.ON_ROUTE_RADIUS_METERS
        )
        if (ceilinged.stage != priced.stage) {
            logger.info { "[ROUTE_PRICE_CEILING] 경로상(직선 ≤ ${CandidateCascadePolicy.ON_ROUTE_RADIUS_METERS.toInt()}m) 최저가 기준 cap, 주유소=${ceilinged.priced.size}건" }
        }

        val capped = ceilinged.filterByPriceCap(hardCap = CandidateCascadePolicy.HARD_CAP)
        if (capped.stage != ceilinged.stage) {
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
        val stationIds = pool.stations.map { it.id }
        val prices = gasStationPriceRepository
            .findAllByIdStationIdInAndIdFuelType(stationIds = stationIds, fuelType = fuelType)
            .associate { it.id.stationId to it.price }

        val missingIds = stationIds.toSet() - prices.keys
        if (missingIds.isNotEmpty()) logger.warn { "가격 정보 없음 (제외): stationIds=$missingIds, fuelType=$fuelType" }

        val priced = pool.stations
            .filter { it.id !in missingIds }
            .map { PricedGasStation(it, prices.getValue(it.id)) }
            .sortedBy { it.price }
        return PricedCandidatePool(priced = priced, stage = pool.stage)
    }

    private data class CandidatePool(
        val stations: List<GasStation>,
        val stage: CandidateSelectionStage
    )

    private data class PricedCandidatePool(
        val priced: List<PricedGasStation>,
        val stage: CandidateSelectionStage
    ) {
        /**
         * 경로상 후보(폴리라인까지 직선 ≤ onRouteRadiusMeters) 중 최저가 p_route를 구하고
         * 가격 ≤ p_route 후보만 보존한다. N ≤ threshold 또는 경로상 후보가 없으면 무변경.
         *
         * 식 (2) 하한 score_i ≥ p_i × ℓ에서, p_i > p_route인 후보는
         * 우회 비용이 0이라도 경로상 최저가 후보를 이길 수 없으므로 외부 호출 전에 배제 가능.
         */
        fun filterByRoutePriceCeiling(
            threshold: Int,
            polyline: List<Coordinate>,
            onRouteRadiusMeters: Double
        ): PricedCandidatePool {
            if (priced.size <= threshold) return this
            val onRoute = priced.filter {
                GeoUtils.calculateMinDistanceToPolyline(it.station.coordinate, polyline) <= onRouteRadiusMeters
            }
            if (onRoute.isEmpty()) return this
            val routeMinPrice = onRoute.minOf { it.price }
            val filtered = priced.filter { it.price <= routeMinPrice }
            return copy(priced = filtered, stage = CandidateSelectionStage.ROUTE_PRICE_CEILING)
        }

        fun filterByPriceCap(hardCap: Int): PricedCandidatePool {
            if (priced.size <= hardCap) return this
            return copy(priced = priced.take(hardCap), stage = CandidateSelectionStage.PRICE_CAPPED)
        }
    }

    /**
     * 파동(wave) 기반 병렬 dispatch로 Kakao 경유 경로를 조회한다.
     *
     * - 첫 파동 = limit: kthBestScore가 초기화되기 전에는 pruning이 수학적으로 불가능하므로,
     *   어차피 호출해야 할 최소 분량을 병렬로 돌린다. (낭비 호출 0)
     * - 이후 파동 = properties.waveSize: 파동 경계에서 price lower bound 검사.
     *   낭비 호출 상한은 pruning이 파동 한가운데 트리거되는 경우의 (waveSize − 1)건.
     * - 후보는 가격 오름차순 정렬이라 파동의 첫 항목(i)의 lower bound만 확인하면 충분.
     */
    private fun rankByPriceLowerBound(
        candidates: List<PricedGasStation>,
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

        var cursor = 0
        var nextWaveSize = limit.coerceAtLeast(1)
        var totalCalled = 0

        while (cursor < candidates.size) {
            if (candidates[cursor].price * refuelLiters > kthBestScore) {
                logger.info { "price lower bound 초과로 탐색 종료: ${candidates.size - cursor}건 pruning" }
                break
            }

            val end = minOf(cursor + nextWaveSize, candidates.size)
            val wave = candidates.subList(cursor, end)
            val routed = dispatchWaveInParallel(wave = wave, origin = origin, destination = destination)
            totalCalled += wave.size

            for ((candidate, routeViaStation) in routed) {
                // null 인 경우 KakaoDirectionsClient가 이미 원인(429/403/4xx/5xx/NETWORK)을 구체적으로 로그.
                // 여기서는 집계(실패 수)만 [WAVE] 완료 로그에 반영한다.
                if (routeViaStation == null) continue
                val actualDetour = (routeViaStation.distanceMeters - baseRoute.distanceMeters).coerceAtLeast(0).toDouble()
                if (actualDetour > detourLimit) {
                    logger.info { "우회 상한 초과 제외: stationId=${candidate.station.id}, detour=${actualDetour.toInt()}m" }
                    continue
                }
                val actualDetourSeconds = (routeViaStation.durationSeconds - baseRoute.durationSeconds).coerceAtLeast(0)
                results += ScoredGasStation(
                    priced = candidate,
                    detourDistanceMeters = actualDetour,
                    detourSeconds = actualDetourSeconds,
                    refuelLiters = refuelLiters,
                    fuelEfficiency = fuelEfficiency,
                    isActualDetour = true
                )
                if (results.size >= limit) {
                    kthBestScore = results.sortedBy { it.score }[limit - 1].score
                }
            }

            cursor = end
            nextWaveSize = properties.waveSize
        }

        logger.info { "Kakao 병렬 호출 완료: 호출=${totalCalled}건, 유효 결과=${results.size}건" }
        return results.sortedBy { it.score }.take(limit)
    }

    private fun dispatchWaveInParallel(
        wave: List<PricedGasStation>,
        origin: Coordinate,
        destination: Coordinate
    ): List<Pair<PricedGasStation, Route?>> {
        val waveStart = System.nanoTime()
        val paired: List<Pair<PricedGasStation, Route?>> = if (wave.size == 1) {
            val only = wave[0]
            val route = kakaoDirectionsClient.searchRouteViaWaypoint(
                origin = origin, destination = destination, waypoint = only.station.coordinate
            )
            listOf(only to route)
        } else {
            val futures = wave.map { candidate ->
                CompletableFuture.supplyAsync({
                    kakaoDirectionsClient.searchRouteViaWaypoint(
                        origin = origin, destination = destination, waypoint = candidate.station.coordinate
                    )
                }, routeExecutor)
            }
            wave.zip(futures) { candidate, future -> candidate to future.join() }
        }
        val waveMs = (System.nanoTime() - waveStart) / 1_000_000
        val nullCount = paired.count { it.second == null }
        logger.info {
            "[WAVE] size=${wave.size}, 실패=${nullCount}, wall=${waveMs}ms, " +
                "stationIds=${wave.map { it.station.id }}"
        }
        return paired
    }
}
