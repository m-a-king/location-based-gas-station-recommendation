package com.kumoh.lbs.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kumoh.lbs.config.ItsProperties
import com.kumoh.lbs.domain.BoundingBox
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ItsClient(
    private val itsRestClient: RestClient,
    private val properties: ItsProperties
) {

    companion object {
        const val BASE_URL = "https://openapi.its.go.kr:9443"
        private val logger = KotlinLogging.logger {}
    }

    fun searchTrafficLinks(box: BoundingBox): List<TrafficLink> {
        return try {
            val response = itsRestClient.get()
                .uri { builder ->
                    builder.path("/trafficInfo")
                        .queryParam("apiKey", properties.apiKey)
                        .queryParam("type", "all")
                        .queryParam("drcType", "all")
                        .queryParam("minX", box.southWest.wgs84.longitude)
                        .queryParam("maxX", box.northEast.wgs84.longitude)
                        .queryParam("minY", box.southWest.wgs84.latitude)
                        .queryParam("maxY", box.northEast.wgs84.latitude)
                        .queryParam("getType", "json")
                        .build()
                }
                .retrieve()
                .body(object : ParameterizedTypeReference<ItsTrafficResponse>() {})

            val links = response?.body?.items ?: emptyList()
            logger.info { "ITS API 응답: ${links.size} 건" }
            links
        } catch (e: Exception) {
            logger.warn { "ITS API 호출 실패, 교통 데이터 없이 진행: ${e.message}" }
            emptyList()
        }
    }
}

data class ItsTrafficResponse(
    @JsonProperty("header") val header: ItsHeader?,
    @JsonProperty("body") val body: ItsBody?
)

data class ItsHeader(
    @JsonProperty("resultCode") val resultCode: String?,
    @JsonProperty("resultMsg") val resultMsg: String?
)

data class ItsBody(
    @JsonProperty("items") val items: List<TrafficLink>?
)

data class TrafficLink(
    @JsonProperty("roadName") val roadName: String?,
    @JsonProperty("linkId") val linkId: String?,
    @JsonProperty("speed") val speed: String?,
    @JsonProperty("travelTime") val travelTime: String?,
    @JsonProperty("createdDate") val createdDate: String?
) {
    fun speedAsDouble(): Double = speed?.toDoubleOrNull() ?: 0.0
}
