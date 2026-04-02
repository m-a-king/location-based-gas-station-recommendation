package com.kumoh.lbs.gasstation.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kumoh.lbs.geo.Coordinate
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.NearbyStation
import com.kumoh.lbs.infra.ExternalApiException
import com.kumoh.lbs.infra.OpinetProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

private val logger = KotlinLogging.logger {}

@Component
class OpinetClient(
    private val opinetRestClient: RestClient,
    private val properties: OpinetProperties
) {

    companion object {
        const val BASE_URL = "https://www.opinet.co.kr/api"
        private const val RESPONSE_TYPE = "json"
    }

    enum class SortType(val code: Int) {
        PRICE(1),
        DISTANCE(2)
    }

    fun searchByRadius(
        center: Coordinate,
        radius: Int,
        fuelType: FuelType,
        sort: SortType
    ): List<NearbyStation> {
        return try {
            val response = opinetRestClient.get()
                .uri {
                    it.path("/aroundAll.do")
                        .queryParam("code", properties.apiKey)
                        .queryParam("x", center.katec.x)
                        .queryParam("y", center.katec.y)
                        .queryParam("radius", radius)
                        .queryParam("prodcd", fuelType.code)
                        .queryParam("sort", sort.code)
                        .queryParam("out", RESPONSE_TYPE)
                        .build()
                }
                .retrieve()
                .body(object : ParameterizedTypeReference<OpinetResponse>() {})
                ?: throw ExternalApiException("OPINET API가 예상치 못한 응답을 반환했습니다.")

            response.result.stations.map { it.toNearbyStation() }
        } catch (e: ExternalApiException) {
            throw e
        } catch (e: Exception) {
            logger.error { "OPINET API 호출 실패: ${e.message}" }
            throw ExternalApiException("주유소 데이터를 불러올 수 없습니다.", e)
        }
    }
}

private data class OpinetResponse(
    @JsonProperty("RESULT") val result: OpinetResult
)

private data class OpinetResult(
    @JsonProperty("OIL") val stations: List<OpinetStation>
)

private data class OpinetStation(
    @JsonProperty("UNI_ID") val stationId: String,
    @JsonProperty("OS_NM") val stationName: String,
    @JsonProperty("POLL_DIV_CD") val brandCode: String,
    @JsonProperty("PRICE") val price: Int,
    @JsonProperty("DISTANCE") val distance: Double,
    @JsonProperty("GIS_X_COOR") val katecX: Double,
    @JsonProperty("GIS_Y_COOR") val katecY: Double
) {
    fun toNearbyStation(): NearbyStation {
        val wgs84 = Coordinate.fromKatec(Coordinate.Katec(katecX, katecY)).wgs84
        return NearbyStation(
            station = GasStation(
                id = stationId,
                name = stationName,
                brand = brandCode,
                latitude = wgs84.latitude,
                longitude = wgs84.longitude
            ),
            price = price,
            distanceMeters = distance
        )
    }
}
