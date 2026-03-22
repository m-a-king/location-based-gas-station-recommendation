package com.kumoh.lbs.service

import com.kumoh.lbs.domain.Coordinate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NearestLinkFinder::class)
@Sql("/data-link-fixture.sql")
class NearestLinkFinderIntegrationTest(
    val nearestLinkFinder: NearestLinkFinder
) {

    // KATEC (400000, 600000) → WGS84 (128.0, 38.0) : KATEC 투영 원점
    private fun stationLocation() = Coordinate.fromKatec(Coordinate.Katec(400000.0, 600000.0))

    @Test
    fun `가장 가까운 링크를 찾는다`() {
        val result = nearestLinkFinder.findNearestLinkId(stationLocation())

        result.shouldBeInstanceOf<NearestLinkFinder.LinkMatchResult.Found>()
        result.linkId shouldBe "3280033641"
    }

    @Test
    fun `거리가 가까운 링크를 우선 선택한다`() {
        val result = nearestLinkFinder.findNearestLinkId(stationLocation())

        result.shouldBeInstanceOf<NearestLinkFinder.LinkMatchResult.Found>()
        result.linkId shouldBe "3280033641"
    }

    @Test
    fun `링크 없는 지역에서 NotFound를 반환한다`() {
        val farLocation = Coordinate.fromKatec(Coordinate.Katec(500000.0, 700000.0))

        val result = nearestLinkFinder.findNearestLinkId(farLocation)

        result.shouldBeInstanceOf<NearestLinkFinder.LinkMatchResult.NotFound>()
    }
}
