package com.kumoh.lbs.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kumoh.lbs.config.OpinetProperties
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.FuelType
import com.kumoh.lbs.domain.GasStation
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

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
    ): List<GasStation> {
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
            ?: return emptyList()

        return response.result.stations.map { it.toGasStation() }
    }
}

data class OpinetResponse(
    @JsonProperty("RESULT") val result: OpinetResult
)

data class OpinetResult(
    @JsonProperty("OIL") val stations: List<OpinetStation>
)

data class OpinetStation(
    @JsonProperty("UNI_ID") val stationId: String,
    @JsonProperty("OS_NM") val stationName: String,
    @JsonProperty("POLL_DIV_CD") val brandCode: String,
    @JsonProperty("PRICE") val price: Int,
    @JsonProperty("DISTANCE") val distance: Double,
    @JsonProperty("GIS_X_COOR") val katecX: Double,
    @JsonProperty("GIS_Y_COOR") val katecY: Double
) {
    fun toGasStation() = GasStation(
        id = stationId,
        name = stationName,
        brand = brandCode,
        location = Coordinate.fromKatec(Coordinate.Katec(katecX, katecY)),
        price = price,
        distance = distance
    )
}
