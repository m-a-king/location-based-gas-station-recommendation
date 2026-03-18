package com.kumoh.lbs.util

import com.kumoh.lbs.domain.Coordinate
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import org.junit.jupiter.api.Test

class CoordinateConverterTest {

    // 서울시청 KATEC 좌표 → WGS84 기대값
    private val seoulCityHallKatec = Coordinate.Katec(309048.0, 552167.0)
    private val expectedLat = 37.5665
    private val expectedLon = 126.9780

    @Test
    fun `서울시청 좌표 변환 정확도`() {
        val result = CoordinateConverter.katecToWgs84(seoulCityHallKatec)

        result.latitude.shouldBeWithinPercentageOf(expectedLat, 0.03)
        result.longitude.shouldBeWithinPercentageOf(expectedLon, 0.01)
    }
}
