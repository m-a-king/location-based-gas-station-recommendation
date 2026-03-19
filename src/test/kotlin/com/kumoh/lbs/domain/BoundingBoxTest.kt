package com.kumoh.lbs.domain

import io.kotest.matchers.doubles.shouldBeGreaterThan
import org.junit.jupiter.api.Test

class BoundingBoxTest {

    @Test
    fun `중위도에서 경도 범위가 위도 범위보다 넓다`() {
        val center = Coordinate.fromWgs84(Coordinate.Wgs84(latitude = 37.0, longitude = 127.0))
        val box = BoundingBox.around(center, 200)

        val latRange = box.northEast.wgs84.latitude - box.southWest.wgs84.latitude
        val lonRange = box.northEast.wgs84.longitude - box.southWest.wgs84.longitude

        // 위도 37도에서 경도 1도 ≈ 88.8km, 위도 1도 ≈ 111.3km
        // 같은 200m를 표현하려면 경도가 더 넓은 범위가 필요
        lonRange shouldBeGreaterThan latRange
    }
}
