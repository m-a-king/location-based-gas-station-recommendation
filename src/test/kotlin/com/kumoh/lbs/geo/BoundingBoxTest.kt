package com.kumoh.lbs.geo

import io.kotest.matchers.doubles.shouldBeGreaterThan
import org.junit.jupiter.api.Test

class BoundingBoxTest {

    @Test
    fun `중위도에서 경도 범위가 위도 범위보다 넓다`() {
        val center = Coordinate.fromWgs84(Coordinate.Wgs84(latitude = 37.0, longitude = 127.0))
        val box = BoundingBox.around(center, 200)

        val latRange = box.northEast.wgs84.latitude - box.southWest.wgs84.latitude
        val lonRange = box.northEast.wgs84.longitude - box.southWest.wgs84.longitude

        lonRange shouldBeGreaterThan latRange
    }
}
