package com.kumoh.lbs.util

import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import org.junit.jupiter.api.Test

class CoordinateConverterTest {

    private val converter = Proj4CoordinateConverter()

    // 서울시청 KATEC 좌표 → WGS84 기대값
    private val seoulCityHallKatecX = 309048.0
    private val seoulCityHallKatecY = 552167.0
    private val expectedLat = 37.5665
    private val expectedLon = 126.9780

    @Test
    fun `서울시청 좌표 변환 정확도`() {
        val result = converter.katecToWgs84(seoulCityHallKatecX, seoulCityHallKatecY)

        result.latitude.shouldBeWithinPercentageOf(expectedLat, 0.03)
        result.longitude.shouldBeWithinPercentageOf(expectedLon, 0.01)
    }
}
