package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.domain.CandidateSelectionStage
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
        private const val N_THRESHOLD = 30
        private const val HARD_CAP = 30
        private const val DETOUR_RATIO = 0.3
        private const val MAX_DETOUR_METERS = 10_000.0
        private const val TIGHT_CORRIDOR_METERS = 2_000.0

        fun maxDetourMeters(baseDistanceMeters: Int): Double =
            minOf(baseDistanceMeters * DETOUR_RATIO, MAX_DETOUR_METERS)
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
        return rankByPriceLowerBound(
            candidates, baseRoute, origin, destination, refuelLiters, fuelEfficiency, limit
        )
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
        // 1단계: POLYLINE_MBR — polyline MBR + 동적 buffer(기본 경로의 30%, 최대 10km)
        val bufferMeters = maxDetourMeters(baseRoute.distanceMeters)
        val mbrBounds = BoundingBox.aroundPolyline(baseRoute.polyline, bufferMeters)
        var stations = gasStationRepository.findInBounds(mbrBounds)
        var stage = CandidateSelectionStage.POLYLINE_MBR
        logger.info { "[POLYLINE_MBR] MBR buffer=${bufferMeters.toInt()}m, 주유소=${stations.size}건" }

        // 2단계: TIGHT_CORRIDOR — polyline distance ≤ 2km 필터
        if (stations.size > N_THRESHOLD) {
            stations = stations.filter {
                GeoUtils.calculateMinDistanceToPolyline(it.coordinate, baseRoute.polyline) <= TIGHT_CORRIDOR_METERS
            }
            stage = CandidateSelectionStage.TIGHT_CORRIDOR
            logger.info { "[TIGHT_CORRIDOR] corridor=${TIGHT_CORRIDOR_METERS.toInt()}m, 주유소=${stations.size}건" }
        }

        // 가격 결합 + 가격 없는 주유소 drop
        val prices = gasStationPriceRepository
            .findAllByIdStationIdInAndIdFuelType(stations.map { it.id }, fuelType)
            .associate { it.id.stationId to it.price }

        val missingIds = stations.map { it.id } - prices.keys
        if (missingIds.isNotEmpty()) logger.warn { "가격 정보 없음 (제외): stationIds=$missingIds, fuelType=$fuelType" }

        var withPrice = stations
            .filter { it.id !in missingIds }
            .map { it to prices.getValue(it.id) }
            .sortedBy { (_, price) -> price }

        // 3단계: PRICE_CAPPED — 가격 오름차순 상위 HARD_CAP개
        if (withPrice.size > HARD_CAP) {
            withPrice = withPrice.take(HARD_CAP)
            stage = CandidateSelectionStage.PRICE_CAPPED
            logger.info { "[PRICE_CAPPED] 상한=${HARD_CAP}개 적용" }
        }

        logger.info { "후보 수집 단계=$stage, 최종 후보=${withPrice.size}건" }
        return withPrice
    }

    // score = price × refuelLiters + (detourKm / fuelEfficiency) × price + 시간비
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
            if (actualDetour > maxDetourMeters(baseRoute.distanceMeters)) {
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
