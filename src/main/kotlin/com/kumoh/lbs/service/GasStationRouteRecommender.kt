package com.kumoh.lbs.service

import com.kumoh.lbs.client.KakaoDirectionsClient
import com.kumoh.lbs.client.OpinetClient
import com.kumoh.lbs.client.OpinetClient.SortType
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import com.kumoh.lbs.domain.ScoredGasStation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

@Service
class GasStationRouteRecommender(
    private val kakaoDirectionsClient: KakaoDirectionsClient,
    private val opinetClient: OpinetClient,
    private val trafficSpeedFinder: TrafficSpeedFinder
) {

    companion object {
        private const val SAMPLE_INTERVAL_METERS = 5000.0
        private const val SEARCH_RADIUS = 2000
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

        val polyline = route.polyline
        if (polyline.isEmpty()) {
            throw IllegalStateException("경로 데이터가 비어 있습니다.")
        }

        val baseDistance = route.distanceMeters

        // STEP 2: 경로 위 주유소 탐색
        val samplePoints = samplePolyline(polyline, SAMPLE_INTERVAL_METERS)
        logger.info { "경로 샘플링: ${polyline.size}개 좌표 → ${samplePoints.size}개 검색 지점" }

        val uniqueStations = samplePoints
            .flatMap { opinetClient.searchByRadius(it, SEARCH_RADIUS, fuelType, SortType.PRICE) }
            .distinctBy { it.id }

        logger.info { "경로 주변 주유소: ${uniqueStations.size}건 (중복 제거 후)" }

        // STEP 3: 1차 필터 (직선거리 기반, 상위 limit × 3)
        val preliminaryCandidates = uniqueStations
            .map { station ->
                val detourDistance = calculateDetourDistance(station.location, polyline)
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

            val actualDetour = (viaRoute.distanceMeters - baseDistance).coerceAtLeast(0).toDouble()
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
            val segDist = haversineMeters(prev, curr)
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
    ): Double {
        val station = stationLocation.wgs84
        var minDistance = Double.MAX_VALUE

        for (i in 0 until polyline.size - 1) {
            val dist = pointToSegmentDistanceMeters(station, polyline[i].wgs84, polyline[i + 1].wgs84)
            if (dist < minDistance) {
                minDistance = dist
            }
        }

        return minDistance * 2
    }

    private fun pointToSegmentDistanceMeters(
        point: Coordinate.Wgs84,
        segStart: Coordinate.Wgs84,
        segEnd: Coordinate.Wgs84
    ): Double {
        val dx = segEnd.longitude - segStart.longitude
        val dy = segEnd.latitude - segStart.latitude
        val lengthSq = dx * dx + dy * dy

        if (lengthSq == 0.0) {
            return haversineMeters(point, segStart)
        }

        val t = ((point.longitude - segStart.longitude) * dx + (point.latitude - segStart.latitude) * dy) / lengthSq
        val clamped = t.coerceIn(0.0, 1.0)

        val closest = Coordinate.Wgs84(
            latitude = segStart.latitude + clamped * dy,
            longitude = segStart.longitude + clamped * dx
        )

        return haversineMeters(point, closest)
    }

    private fun haversineMeters(
        a: Coordinate.Wgs84,
        b: Coordinate.Wgs84
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon

        return 2 * earthRadius * asin(sqrt(h))
    }
}
