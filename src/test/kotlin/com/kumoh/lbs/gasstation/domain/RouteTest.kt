package com.kumoh.lbs.gasstation.domain

import com.kumoh.lbs.geo.Coordinate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RouteTest {

    private val validPoint = Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0))

    @Test
    fun `빈 polyline으로 생성하면 예외를 던진다`() {
        assertThrows<IllegalArgumentException> {
            Route(polyline = emptyList(), distanceMeters = 1000)
        }
    }

    @Test
    fun `distanceMeters가 0이면 예외를 던진다`() {
        assertThrows<IllegalArgumentException> {
            Route(polyline = listOf(validPoint), distanceMeters = 0)
        }
    }

    @Test
    fun `distanceMeters가 음수면 예외를 던진다`() {
        assertThrows<IllegalArgumentException> {
            Route(polyline = listOf(validPoint), distanceMeters = -1)
        }
    }
}
