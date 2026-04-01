package com.kumoh.lbs.gasstation.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kumoh.lbs.geo.Coordinate
import com.kumoh.lbs.gasstation.domain.Route
import com.kumoh.lbs.infra.KakaoProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

private val logger = KotlinLogging.logger {}

@Component
class KakaoDirectionsClient(
    private val kakaoRestClient: RestClient,
    private val properties: KakaoProperties
) {

    companion object {
        const val BASE_URL = "https://apis-navi.kakaomobility.com"
    }

    fun searchRoute(
        origin: Coordinate,
        destination: Coordinate
    ): Route? =
        requestRoute(origin, destination)

    fun searchRouteViaWaypoint(
        origin: Coordinate,
        destination: Coordinate,
        waypoint: Coordinate
    ): Route? =
        requestRoute(origin, destination, waypoint)

    private fun requestRoute(
        origin: Coordinate,
        destination: Coordinate,
        waypoint: Coordinate? = null
    ): Route? {
        return try {
            val response = kakaoRestClient.get()
                .uri { builder ->
                    builder.path("/v1/directions")
                        .queryParam("origin", origin.toLngLatString())
                        .queryParam("destination", destination.toLngLatString())
                        .queryParam("priority", "RECOMMEND")
                    waypoint?.let { builder.queryParam("waypoints", it.toLngLatString()) }
                    builder.build()
                }
                .header("Authorization", "KakaoAK ${properties.apiKey}")
                .retrieve()
                .body(object : ParameterizedTypeReference<KakaoDirectionsResponse>() {})

            val kakaoRoute = response?.routes?.firstOrNull()
            if (kakaoRoute == null || kakaoRoute.resultCode != 0) {
                logger.warn { "카카오 길찾기 실패: resultCode=${kakaoRoute?.resultCode}" }
                return null
            }

            val distance = kakaoRoute.summary?.distance
            if (distance == null) {
                logger.warn { "카카오 길찾기 응답에 거리 정보 없음" }
                return null
            }

            val polyline = kakaoRoute.extractPolyline()
            logger.info { "카카오 길찾기 성공: 거리=${distance}m, 폴리라인=${polyline.size}개 좌표" }

            Route(polyline = polyline, distanceMeters = distance)
        } catch (e: Exception) {
            logger.warn { "카카오 길찾기 API 호출 실패: ${e.message}" }
            null
        }
    }
}

data class KakaoDirectionsResponse(
    @JsonProperty("trans_id") val transId: String?,
    @JsonProperty("routes") val routes: List<KakaoRoute>?
)

data class KakaoRoute(
    @JsonProperty("result_code") val resultCode: Int?,
    @JsonProperty("result_msg") val resultMsg: String?,
    @JsonProperty("summary") val summary: KakaoRouteSummary?,
    @JsonProperty("sections") val sections: List<KakaoSection>?
) {
    fun extractPolyline(): List<Coordinate> {
        return sections?.flatMap {
            it.roads?.flatMap { road -> road.toCoordinates() } ?: emptyList()
        } ?: emptyList()
    }
}

data class KakaoRouteSummary(
    @JsonProperty("distance") val distance: Int?,
    @JsonProperty("duration") val duration: Int?,
    @JsonProperty("fare") val fare: KakaoFare?
)

data class KakaoFare(
    @JsonProperty("taxi") val taxi: Int?,
    @JsonProperty("toll") val toll: Int?
)

data class KakaoSection(
    @JsonProperty("distance") val distance: Int?,
    @JsonProperty("duration") val duration: Int?,
    @JsonProperty("roads") val roads: List<KakaoRoad>?
)

data class KakaoRoad(
    @JsonProperty("name") val name: String?,
    @JsonProperty("distance") val distance: Int?,
    @JsonProperty("duration") val duration: Int?,
    @JsonProperty("traffic_speed") val trafficSpeed: Double?,
    @JsonProperty("traffic_state") val trafficState: Int?,
    @JsonProperty("vertexes") val vertexes: List<Double>?
) {
    fun toCoordinates(): List<Coordinate> {
        val v = vertexes ?: return emptyList()
        return (v.indices step 2)
            .filter { it + 1 < v.size }
            .map { Coordinate.fromWgs84(Coordinate.Wgs84(latitude = v[it + 1], longitude = v[it])) }
    }
}

private fun Coordinate.toLngLatString() = "${wgs84.longitude},${wgs84.latitude}"
