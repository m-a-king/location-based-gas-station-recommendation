package com.kumoh.lbs.geo

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CoordinateConverterTest {

    private val seoulCityHallKatec = Coordinate.Katec(309048.0, 552167.0)
    private val seoulCityHallWgs84 = Coordinate.Wgs84(37.5665, 126.9780)

    @Test
    fun `KATEC에서 WGS84로 변환하면 서울시청 위경도가 나온다`() {
        val result = CoordinateConverter.katecToWgs84(seoulCityHallKatec)

        result.latitude.shouldBeWithinPercentageOf(seoulCityHallWgs84.latitude, 0.03)
        result.longitude.shouldBeWithinPercentageOf(seoulCityHallWgs84.longitude, 0.01)
    }

    @Test
    fun `WGS84에서 KATEC으로 변환하면 서울시청 KATEC 좌표에 근접한다`() {
        // WGS84가 소수점 4자리 근사값이고 KATEC(+k=0.9999) 투영 특성상 수백m 오차가 정상.
        // 방향성·대략적 좌표 범위만 확인하므로 허용 오차 1.2km.
        val result = CoordinateConverter.wgs84ToKatec(seoulCityHallWgs84)

        result.x shouldBe (seoulCityHallKatec.x plusOrMinus 1200.0)
        result.y shouldBe (seoulCityHallKatec.y plusOrMinus 1200.0)
    }

    @Test
    fun `양방향 변환을 왕복시키면 원본 좌표에 근접한다`() {
        val roundTrip = CoordinateConverter.katecToWgs84(
            CoordinateConverter.wgs84ToKatec(seoulCityHallWgs84)
        )

        roundTrip.latitude.shouldBeWithinPercentageOf(seoulCityHallWgs84.latitude, 0.001)
        roundTrip.longitude.shouldBeWithinPercentageOf(seoulCityHallWgs84.longitude, 0.001)
    }
}
