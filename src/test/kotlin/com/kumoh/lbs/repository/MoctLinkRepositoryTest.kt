package com.kumoh.lbs.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/data-link-fixture.sql")
class MoctLinkRepositoryTest {

    @Autowired
    lateinit var moctLinkRepository: MoctLinkRepository

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

        // fixture에서 bbox 안에 노드가 있는 링크: 3280033641, 3280033642, 1050012345, 3280033650
        // 9999999999는 bbox 밖이므로 제외
        val linkIds = links.map { it.linkId }.toSet()
        linkIds shouldBe setOf("3280033641", "3280033642", "1050012345", "3280033650")
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
    fun `t_node 좌표만 범위 안에 있는 링크도 조회된다`() {
        // 이면도로 3280033650: f(128.0010, 37.9980) → t(128.0010, 38.0005)
        // f_latitude=37.9980은 bbox 밖, t_latitude=38.0005는 bbox 안
        // 좁은 bbox로 f_node는 밖이지만 t_node는 안에 있는 경우 테스트
        val links = moctLinkRepository.findLinksInBoundingBox(
            minLon = 128.0008,
            maxLon = 128.0012,
            minLat = 38.0003,
            maxLat = 38.0007
        )

        val linkIds = links.map { it.linkId }
        linkIds shouldHaveSize 1
        linkIds[0] shouldBe "3280033650"
    }
}
