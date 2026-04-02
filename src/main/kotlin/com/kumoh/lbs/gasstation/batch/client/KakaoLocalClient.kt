package com.kumoh.lbs.gasstation.batch.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kumoh.lbs.geo.Coordinate
import com.kumoh.lbs.infra.KakaoProperties
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
        private const val KAKAO_AUTH_PREFIX = "KakaoAK "
    }

    fun resolveCoordinates(address: String): Coordinate.Wgs84? {
        return try {
            val response = kakaoLocalRestClient.get()
                .uri { builder ->
                    builder.path("/v2/local/search/address.json")
                        .queryParam("query", address)
                        .build()
                }
                .header("Authorization", "$KAKAO_AUTH_PREFIX${properties.apiKey}")
                .retrieve()
                .body(object : ParameterizedTypeReference<KakaoAddressResponse>() {})

            val document = response?.documents?.firstOrNull()
                ?: return null.also { logger.warn { "주소 좌표 변환 결과 없음: $address" } }

            val latitude = document.y.toDoubleOrNull()
                ?: return null.also { logger.warn { "좌표 파싱 실패: address=$address, x=${document.x}, y=${document.y}" } }
            val longitude = document.x.toDoubleOrNull()
                ?: return null.also { logger.warn { "좌표 파싱 실패: address=$address, x=${document.x}, y=${document.y}" } }

            Coordinate.Wgs84(latitude, longitude)
        } catch (e: Exception) {
            logger.warn { "카카오 로컬 API 호출 실패 (address=$address): ${e.message}" }
            null
        }
    }
}

private data class KakaoAddressResponse(
    @JsonProperty("meta") val meta: KakaoAddressMeta?,
    @JsonProperty("documents") val documents: List<KakaoAddressDocument>?
)

private data class KakaoAddressMeta(
    @JsonProperty("total_count") val totalCount: Int?
)

private data class KakaoAddressDocument(
    @JsonProperty("address_name") val addressName: String?,
    @JsonProperty("address_type") val addressType: String?,
    @JsonProperty("x") val x: String,  // 경도 (longitude)
    @JsonProperty("y") val y: String   // 위도 (latitude)
)
