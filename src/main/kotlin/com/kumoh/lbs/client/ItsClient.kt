package com.kumoh.lbs.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kumoh.lbs.config.ItsProperties
import org.slf4j.LoggerFactory
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
    }
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 영역 내 실시간 교통소통정보를 조회합니다.
     * @param minX 최소 경도 (WGS84)
     * @param maxX 최대 경도 (WGS84)
     * @param minY 최소 위도 (WGS84)
     * @param maxY 최대 위도 (WGS84)
     * @return 도로 구간별 통행 속도 목록
     */
    fun searchTrafficLinks(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double
    ): List<TrafficLink> {
        return try {
            val response = itsRestClient.get()
                .uri { builder ->
                    builder.path("/trafficInfo")
                        .queryParam("apiKey", properties.apiKey)
                        .queryParam("type", "all")
                        .queryParam("drcType", "all")
                        .queryParam("minX", minX)
                        .queryParam("maxX", maxX)
                        .queryParam("minY", minY)
                        .queryParam("maxY", maxY)
                        .queryParam("getType", "json")
                        .build()
                }
                .retrieve()
                .body(object : ParameterizedTypeReference<ItsTrafficResponse>() {})

            val links = response?.body?.items ?: emptyList()
            log.info("ITS API 응답: {} 건, 영역=[{},{},{},{}]", links.size, minX, minY, maxX, maxY)
            links
        } catch (e: Exception) {
            log.warn("ITS API 호출 실패, 교통 데이터 없이 진행: {}", e.message)
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
