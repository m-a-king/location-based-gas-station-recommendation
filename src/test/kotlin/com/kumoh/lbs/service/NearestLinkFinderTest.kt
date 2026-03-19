package com.kumoh.lbs.service

import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.domain.entity.MoctLink
import com.kumoh.lbs.repository.MoctLinkRepository
import com.kumoh.lbs.service.NearestLinkFinder.LinkMatchResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class NearestLinkFinderTest {

    @Mock
    lateinit var moctLinkRepository: MoctLinkRepository

    @InjectMocks
    lateinit var nearestLinkFinder: NearestLinkFinder

    private fun stationAt(lat: Double = 37.0, lon: Double = 127.0) =
        Coordinate.fromWgs84(Coordinate.Wgs84(lat, lon))

    private fun moctLink(
        linkId: String,
        fLon: Double, fLat: Double,
        tLon: Double, tLat: Double
    ) = MoctLink(
        linkId = linkId,
        fNode = "F$linkId",
        tNode = "T$linkId",
        roadName = "테스트도로",
        roadRank = "103",
        roadNo = null,
        lanes = 2,
        maxSpd = 60,
        length = 500.0,
        fLongitude = fLon,
        fLatitude = fLat,
        tLongitude = tLon,
        tLatitude = tLat
    )

    @Test
    fun `링크가 없으면 NotFound를 반환한다`() {
        val location = stationAt()
        whenever(moctLinkRepository.findLinksIn(any()))
            .thenReturn(emptyList())

        val result = nearestLinkFinder.findNearestLinkId(location)

        result.shouldBeInstanceOf<LinkMatchResult.NotFound>()
    }

    @Test
    fun `후보가 1개면 해당 linkId를 반환한다`() {
        val location = stationAt()
        val link = moctLink("LINK001", 126.999, 37.0, 127.001, 37.0)
        whenever(moctLinkRepository.findLinksIn(any()))
            .thenReturn(listOf(link))

        val result = nearestLinkFinder.findNearestLinkId(location)

        result shouldBe LinkMatchResult.Found("LINK001")
    }

    @Test
    fun `가장 가까운 링크를 선택한다`() {
        val location = stationAt(lat = 37.0, lon = 127.0)

        val near = moctLink("NEAR", 127.0001, 37.0005, 127.0001, 36.9995)
        val far = moctLink("FAR", 127.005, 37.005, 127.005, 36.995)

        whenever(moctLinkRepository.findLinksIn(any()))
            .thenReturn(listOf(far, near))

        val result = nearestLinkFinder.findNearestLinkId(location)

        result shouldBe LinkMatchResult.Found("NEAR")
    }

    @Test
    fun `200m 밖의 링크만 있으면 NotFound를 반환한다`() {
        val location = stationAt(lat = 37.0, lon = 127.0)
        // 0.005도 ≈ 556m, 200m 초과
        val farLink = moctLink("FAR001", 127.0, 37.005, 127.001, 37.005)
        whenever(moctLinkRepository.findLinksIn(any()))
            .thenReturn(listOf(farLink))

        val result = nearestLinkFinder.findNearestLinkId(location)

        result.shouldBeInstanceOf<LinkMatchResult.NotFound>()
    }
}
