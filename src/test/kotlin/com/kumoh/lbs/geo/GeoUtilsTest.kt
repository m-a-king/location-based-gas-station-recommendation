package com.kumoh.lbs.geo

import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeoUtilsTest {

    // ─── calculateHaversineDistance ──────────────────────────────────────────

    @Test
    fun `같은 좌표 사이의 거리는 0이다`() {
        val p = Coordinate.Wgs84(37.5665, 126.9780)
        GeoUtils.calculateHaversineDistance(p, p) shouldBe 0.0
    }

    @Test
    fun `서울시청-부산시청 거리는 약 325km다`() {
        val seoul = Coordinate.Wgs84(37.5665, 126.9780)
        val busan = Coordinate.Wgs84(35.1796, 129.0756)
        val distance = GeoUtils.calculateHaversineDistance(seoul, busan)
        distance shouldBe (325_000.0 plusOrMinus 5_000.0)
    }

    // ─── calculatePointToSegmentDistance ─────────────────────────────────────

    @Test
    fun `점이 선분 위에 있으면 거리는 거의 0이다`() {
        val start = Coordinate.Wgs84(37.0, 127.0)
        val end   = Coordinate.Wgs84(37.1, 127.0)
        val mid   = Coordinate.Wgs84(37.05, 127.0)  // 선분 정중앙

        val dist = GeoUtils.calculatePointToSegmentDistance(mid, start, end)

        dist shouldBeLessThan 1.0  // 1m 미만
    }

    @Test
    fun `수선의 발이 선분 밖이면 가장 가까운 끝점까지의 거리를 반환한다`() {
        val start = Coordinate.Wgs84(37.0, 127.0)
        val end   = Coordinate.Wgs84(37.1, 127.0)
        val beyond = Coordinate.Wgs84(37.2, 127.0)  // end 너머

        val distToSegment = GeoUtils.calculatePointToSegmentDistance(beyond, start, end)
        val distToEnd     = GeoUtils.calculateHaversineDistance(beyond, end)

        distToSegment shouldBe (distToEnd plusOrMinus 1.0)
    }

    @Test
    fun `시작점과 끝점이 같은 degenerate 선분은 해당 점까지의 거리를 반환한다`() {
        val point    = Coordinate.Wgs84(37.05, 127.01)
        val degenerate = Coordinate.Wgs84(37.05, 127.0)

        val distToSegment = GeoUtils.calculatePointToSegmentDistance(point, degenerate, degenerate)
        val distToPoint   = GeoUtils.calculateHaversineDistance(point, degenerate)

        distToSegment shouldBe (distToPoint plusOrMinus 1.0)
    }

    // ─── calculateMinDistanceToPolyline ──────────────────────────────────────

    @Test
    fun `여러 선분 중 가장 가까운 선분까지의 거리를 반환한다`() {
        val polyline = listOf(
            Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.1, 127.0)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.2, 127.0))
        )
        // 두 번째 선분(37.1~37.2) 근처 점
        val nearSecond = Coordinate.fromWgs84(Coordinate.Wgs84(37.15, 127.005))

        val minDist = GeoUtils.calculateMinDistanceToPolyline(nearSecond, polyline)

        // 두 번째 선분까지 거리는 약 443m, 첫 번째 선분까지는 훨씬 멀어야 한다
        minDist shouldBeLessThan 500.0
    }

    @Test
    fun `polyline이 점 1개이면 zipWithNext가 비어 minOf가 예외를 던진다`() {
        val singlePoint = listOf(Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0)))
        val point = Coordinate.fromWgs84(Coordinate.Wgs84(37.05, 127.0))

        assertThrows<NoSuchElementException> {
            GeoUtils.calculateMinDistanceToPolyline(point, singlePoint)
        }
    }

    @Test
    fun `경로에서 멀리 떨어진 점은 큰 거리를 반환한다`() {
        val polyline = listOf(
            Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0)),
            Coordinate.fromWgs84(Coordinate.Wgs84(37.1, 127.0))
        )
        val farPoint = Coordinate.fromWgs84(Coordinate.Wgs84(37.05, 127.1))  // 약 8.9km 동쪽

        val dist = GeoUtils.calculateMinDistanceToPolyline(farPoint, polyline)

        dist shouldBeGreaterThan 8_000.0
    }
}
