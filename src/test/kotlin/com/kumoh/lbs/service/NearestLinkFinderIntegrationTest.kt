package com.kumoh.lbs.service

import com.kumoh.lbs.util.Proj4CoordinateConverter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(Proj4CoordinateConverter::class, NearestLinkFinder::class)
@Sql("/data-link-fixture.sql")
class NearestLinkFinderIntegrationTest {

    @Autowired
    lateinit var nearestLinkFinder: NearestLinkFinder

    // KATEC (400000, 600000) → WGS84 (128.0, 38.0) : KATEC 투영 원점
    private val stationKatecX = 400000.0
    private val stationKatecY = 600000.0

    @Test
    fun `가장 가까운 링크를 찾는다`() {
        val result = nearestLinkFinder.findNearestLinkId(
            stationKatecX, stationKatecY
        )

        result.shouldBeInstanceOf<NearestLinkFinder.LinkMatchResult.Found>()
        // 주유소에서 가장 가까운 링크 (거리 ~11m)
        // 동행/서행 중 하나가 선택됨 — 방향 구분 없이 거리만으로 판단
        result.linkId shouldBe "3280033641"
    }

    @Test
    fun `거리가 가까운 링크를 우선 선택한다`() {
        val result = nearestLinkFinder.findNearestLinkId(
            stationKatecX, stationKatecY
        )

        result.shouldBeInstanceOf<NearestLinkFinder.LinkMatchResult.Found>()
        // 고속도로 1050012345(167m)가 아닌 주유소 앞 도로(11m)가 선택되어야 함
        result.linkId shouldBe "3280033641"
    }

    @Test
    fun `링크 없는 지역에서 NotFound를 반환한다`() {
        // 주유소가 아무 도로도 없는 좌표에 있는 경우
        // KATEC (500000, 700000) → 링크가 없는 지역
        val result = nearestLinkFinder.findNearestLinkId(
            500000.0, 700000.0
        )

        result.shouldBeInstanceOf<NearestLinkFinder.LinkMatchResult.NotFound>()
    }
}
