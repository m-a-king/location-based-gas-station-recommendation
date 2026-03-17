package com.kumoh.lbs.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DirectionCalculationTest {

    @Test
    fun `평행 벡터 코사인 유사도는 1이다`() {
        cosineSimilarity(1.0, 0.0, 2.0, 0.0) shouldBe 1.0
    }

    @Test
    fun `반대 벡터 코사인 유사도는 -1이다`() {
        cosineSimilarity(1.0, 0.0, -1.0, 0.0) shouldBe -1.0
    }

    @Test
    fun `수직 벡터 코사인 유사도는 0이다`() {
        cosineSimilarity(1.0, 0.0, 0.0, 1.0) shouldBe 0.0
    }

    @Test
    fun `영벡터가 포함되면 코사인 유사도는 0이다`() {
        cosineSimilarity(0.0, 0.0, 1.0, 1.0) shouldBe 0.0
    }

    @Test
    fun `점이 선분 위에 있으면 거리는 0이다`() {
        pointToSegmentDistance(0.5, 0.0, 0.0, 0.0, 1.0, 0.0) shouldBe 0.0
    }

    @Test
    fun `점에서 선분까지 수직 거리를 계산한다`() {
        // (0,1)에서 x축 선분 (0,0)-(1,0)까지 거리 = 1
        pointToSegmentDistance(0.5, 1.0, 0.0, 0.0, 1.0, 0.0) shouldBe 1.0
    }

    @Test
    fun `점이 선분 연장선 밖에 있으면 끝점까지 거리를 반환한다`() {
        // (2,0)에서 선분 (0,0)-(1,0)까지 거리 = 1
        pointToSegmentDistance(2.0, 0.0, 0.0, 0.0, 1.0, 0.0) shouldBe 1.0
    }

    @Test
    fun `선분이 점인 경우 점까지 거리를 반환한다`() {
        pointToSegmentDistance(3.0, 4.0, 0.0, 0.0, 0.0, 0.0) shouldBe 5.0
    }
}
