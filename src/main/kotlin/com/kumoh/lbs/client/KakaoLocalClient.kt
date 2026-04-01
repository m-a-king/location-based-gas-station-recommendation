package com.kumoh.lbs.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kumoh.lbs.config.KakaoProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

private val logger = KotlinLogging.logger {}

@Component
class KakaoLocalClient(
    private val kakaoLocalRestClient: RestClient,
    private val properties: KakaoProperties
) {
    companion object {
        const val BASE_URL = "https://dapi.kakao.com"
    }

    /**
     * 주소를 WGS84 좌표(위도, 경도)로 변환한다.
     * 변환 실패 시 null 반환.
     */
    fun geocode(address: String): Pair<Double, Double>? {
        return try {
            val response = kakaoLocalRestClient.get()
                .uri { builder ->
                    builder.path("/v2/local/search/address.json")
                        .queryParam("query", address)
                        .build()
                }
                .header("Authorization", "KakaoAK ${properties.apiKey}")
                .retrieve()
                .body(object : ParameterizedTypeReference<KakaoAddressResponse>() {})

            val document = response?.documents?.firstOrNull()
            if (document == null) {
                logger.warn { "주소 좌표 변환 결과 없음: $address" }
                return null
            }

            val latitude = document.y.toDoubleOrNull()
            val longitude = document.x.toDoubleOrNull()
            if (latitude == null || longitude == null) {
                logger.warn { "좌표 파싱 실패: address=$address, x=${document.x}, y=${document.y}" }
                return null
            }

            latitude to longitude
        } catch (e: Exception) {
            logger.warn { "카카오 로컬 API 호출 실패 (address=$address): ${e.message}" }
            null
        }
    }
}

data class KakaoAddressResponse(
    @JsonProperty("meta") val meta: KakaoAddressMeta?,
    @JsonProperty("documents") val documents: List<KakaoAddressDocument>?
)

data class KakaoAddressMeta(
    @JsonProperty("total_count") val totalCount: Int?
)

data class KakaoAddressDocument(
    @JsonProperty("address_name") val addressName: String?,
    @JsonProperty("address_type") val addressType: String?,
    @JsonProperty("x") val x: String,  // 경도 (longitude)
    @JsonProperty("y") val y: String   // 위도 (latitude)
)
