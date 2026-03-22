package com.kumoh.lbs.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/data-link-fixture.sql")
class MoctLinkRepositoryTest(
    val moctLinkRepository: MoctLinkRepository
) {

    // 주유소 WGS84 좌표: (128.0, 38.0)
    // 검색 반경 200m ≈ 0.0018도
    private val stationLon = 128.0
    private val stationLat = 38.0
    private val radiusDeg = 200.0 / 111_000.0

    @Test
    fun `bounding box 안의 링크가 정확히 조회된다`() {
        val links = moctLinkRepository.findLinksInBoundingBox(
            minLon = stationLon - radiusDeg,
            maxLon = stationLon + radiusDeg,
            minLat = stationLat - radiusDeg,
            maxLat = stationLat + radiusDeg
        )

        // fixture에서 bbox와 교차하는 링크: 3280033641, 3280033642, 1050012345, 3280033650, CROSSING001
        // 9999999999는 bbox 밖이므로 제외
        val linkIds = links.map { it.linkId }.toSet()
        linkIds shouldBe setOf("3280033641", "3280033642", "1050012345", "3280033650", "CROSSING001")
    }

    @Test
    fun `범위 밖 좌표는 빈 리스트를 반환한다`() {
        val links = moctLinkRepository.findLinksInBoundingBox(
            minLon = 130.0,
            maxLon = 130.002,
            minLat = 40.0,
            maxLat = 40.002
        )

        links.shouldBeEmpty()
    }

    @Test
    fun `양쪽 endpoint가 bbox 밖이지만 관통하는 링크도 조회된다`() {
        val links = moctLinkRepository.findLinksInBoundingBox(
            minLon = stationLon - radiusDeg,
            maxLon = stationLon + radiusDeg,
            minLat = stationLat - radiusDeg,
            maxLat = stationLat + radiusDeg
        )

        val linkIds = links.map { it.linkId }
        linkIds shouldContain "CROSSING001"
    }

    @Test
    fun `선분이 bbox를 지나는 링크도 조회된다`() {
        // 이면도로 3280033650: f(128.0010, 37.9980) → t(128.0010, 38.0005)
        // 좁은 bbox에서 선분의 AABB가 겹치면 조회됨
        val links = moctLinkRepository.findLinksInBoundingBox(
            minLon = 128.0008,
            maxLon = 128.0012,
            minLat = 38.0003,
            maxLat = 38.0007
        )

        val linkIds = links.map { it.linkId }
        linkIds shouldContain "3280033650"
    }
}
