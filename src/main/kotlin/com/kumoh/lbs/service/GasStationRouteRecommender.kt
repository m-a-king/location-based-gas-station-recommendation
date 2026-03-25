package com.kumoh.lbs.service

import com.kumoh.lbs.client.KakaoDirectionsClient
import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetClient.SortType
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import com.kumoh.lbs.domain.ScoredGasStation
import com.kumoh.lbs.util.GeoUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class GasStationRouteRecommender(
    private val kakaoDirectionsClient: KakaoDirectionsClient,
    private val opinetClient: OpinetClient,
    private val trafficSpeedFinder: TrafficSpeedFinder
) {

    companion object {
        private const val SEARCH_RADIUS = 5000
        private const val MAX_SAMPLE_POINTS = 20
        private const val MIN_INTERVAL_METERS = SEARCH_RADIUS.toDouble()
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
        // STEP 1: 기본 경로 조회
        val route = kakaoDirectionsClient.searchRoute(origin, destination)
            ?: throw IllegalStateException("경로를 찾을 수 없습니다.")

        // STEP 2: 경로 위 주유소 탐색
        val interval = maxOf(route.distanceMeters.toDouble() / MAX_SAMPLE_POINTS, MIN_INTERVAL_METERS)
        val samplePoints = samplePolyline(route.polyline, interval)
        logger.info { "경로 샘플링: ${route.polyline.size}개 좌표 → ${samplePoints.size}개 검색 지점" }

        val uniqueStations = samplePoints
            .flatMap { opinetClient.searchByRadius(it, SEARCH_RADIUS, fuelType, SortType.PRICE) }
            .distinctBy { it.id }

        logger.info { "경로 주변 주유소: ${uniqueStations.size}건 (중복 제거 후)" }

        // STEP 3: 1차 필터 (직선거리 기반, 상위 limit × 3)
        val preliminaryCandidates = uniqueStations
            .map { station ->
                val detourDistance = calculateDetourDistance(station.location, route.polyline)
                ScoredGasStation.ofWithDetour(station, refuelLiters, fuelEfficiency, detourDistance)
            }
            .sortedBy { it.score }
            .take(limit * PRELIMINARY_FILTER_MULTIPLIER)

        logger.info { "1차 필터 통과: ${preliminaryCandidates.size}건" }

        // STEP 4: 2차 정밀 계산 (실제 경유 경로 거리)
        val refinedCandidates = preliminaryCandidates.mapNotNull { scored ->
            val viaRoute = kakaoDirectionsClient.searchRouteViaWaypoint(
                origin, destination, scored.station.location
            )
            if (viaRoute == null) {
                logger.debug { "경유 경로 조회 실패: ${scored.station.name}, 1차 점수 유지" }
                return@mapNotNull scored
            }

            val actualDetour = (viaRoute.distanceMeters - route.distanceMeters).coerceAtLeast(0).toDouble()
            ScoredGasStation.ofWithDetour(scored.station, refuelLiters, fuelEfficiency, actualDetour)
        }

        // STEP 5: 최종 정렬 + 교통 속도 부착
        val topCandidates = refinedCandidates
            .sortedBy { it.score }
            .take(limit)

        return topCandidates.map {
            it.withFrontRoadSpeed(trafficSpeedFinder.findAt(it.station.location))
        }
    }

    private fun samplePolyline(
        polyline: List<Coordinate>,
        intervalMeters: Double
    ): List<Coordinate> {
        if (polyline.size <= 1) return polyline

        val sampled = mutableListOf(polyline.first())
        var accumulated = 0.0

        for (i in 1 until polyline.size) {
            val prev = polyline[i - 1].wgs84
            val curr = polyline[i].wgs84
            val segDist = GeoUtils.haversineMeters(prev, curr)
            accumulated += segDist

            while (accumulated >= intervalMeters) {
                val overshoot = accumulated - intervalMeters
                val ratio = if (segDist > 0) 1.0 - overshoot / segDist else 1.0
                val interpolated = Coordinate.fromWgs84(Coordinate.Wgs84(
                    latitude = prev.latitude + ratio * (curr.latitude - prev.latitude),
                    longitude = prev.longitude + ratio * (curr.longitude - prev.longitude)
                ))
                sampled.add(interpolated)
                accumulated = overshoot
            }
        }

        if (sampled.last() != polyline.last()) {
            sampled.add(polyline.last())
        }

        return sampled
    }

    private fun calculateDetourDistance(
        stationLocation: Coordinate,
        polyline: List<Coordinate>
    ): Double =
        GeoUtils.minDistanceToPolylineMeters(stationLocation, polyline) * 2
}
