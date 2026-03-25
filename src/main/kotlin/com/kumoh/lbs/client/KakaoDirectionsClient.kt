package com.kumoh.lbs.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kumoh.lbs.config.KakaoProperties
import com.kumoh.lbs.domain.Coordinate
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

    fun searchRoute(origin: Coordinate, destination: Coordinate): KakaoRoute? =
        requestRoute(origin, destination)

    fun searchRouteViaWaypoint(
        origin: Coordinate,
        destination: Coordinate,
        waypoint: Coordinate
    ): KakaoRoute? =
        requestRoute(origin, destination, waypoint)

    private fun requestRoute(
        origin: Coordinate,
        destination: Coordinate,
        waypoint: Coordinate? = null
    ): KakaoRoute? {
        return try {
            val response = kakaoRestClient.get()
                .uri { builder ->
                    builder.path("/v1/directions")
                        .queryParam("origin", "${origin.wgs84.longitude},${origin.wgs84.latitude}")
                        .queryParam("destination", "${destination.wgs84.longitude},${destination.wgs84.latitude}")
                        .queryParam("priority", "RECOMMEND")
                    if (waypoint != null) {
                        builder.queryParam("waypoints", "${waypoint.wgs84.longitude},${waypoint.wgs84.latitude}")
                    }
                    builder.build()
                }
                .header("Authorization", "KakaoAK ${properties.apiKey}")
                .retrieve()
                .body(object : ParameterizedTypeReference<KakaoDirectionsResponse>() {})

            val route = response?.routes?.firstOrNull()
            if (route == null || route.resultCode != 0) {
                logger.warn { "카카오 길찾기 실패: resultCode=${route?.resultCode}" }
                return null
            }

            logger.info { "카카오 길찾기 성공: 거리=${route.summary?.distance}m" }
            route
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
    fun extractPolyline(): List<Coordinate.Wgs84> {
        return sections?.flatMap { section ->
            section.roads?.flatMap { road ->
                road.vertexPairs()
            } ?: emptyList()
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
    fun vertexPairs(): List<Coordinate.Wgs84> {
        val v = vertexes ?: return emptyList()
        return (v.indices step 2)
            .filter { it + 1 < v.size }
            .map { i -> Coordinate.Wgs84(latitude = v[i + 1], longitude = v[i]) }
    }
}