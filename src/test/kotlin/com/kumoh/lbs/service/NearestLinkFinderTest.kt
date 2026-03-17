package com.kumoh.lbs.service

import com.kumoh.lbs.domain.entity.MoctLink
import com.kumoh.lbs.repository.MoctLinkRepository
import com.kumoh.lbs.domain.LatLon
import com.kumoh.lbs.util.CoordinateConverter
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

    @Mock
    lateinit var coordinateConverter: CoordinateConverter

    @InjectMocks
    lateinit var nearestLinkFinder: NearestLinkFinder

    private fun stubCoordinates(
        stationLat: Double = 37.0,
        stationLon: Double = 127.0,
        userLat: Double = 37.01,
        userLon: Double = 127.0
    ) {
        // 첫 번째 호출: 주유소 좌표, 두 번째 호출: 사용자 좌표
        whenever(coordinateConverter.katecToWgs84(any(), any()))
            .thenReturn(LatLon(stationLat, stationLon))
            .thenReturn(LatLon(userLat, userLon))
    }

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
        stubCoordinates()
        whenever(moctLinkRepository.findLinksInBoundingBox(any(), any(), any(), any()))
            .thenReturn(emptyList())

        val result = nearestLinkFinder.findNearestLinkId(100.0, 200.0, 100.0, 210.0)

        result.shouldBeInstanceOf<LinkMatchResult.NotFound>()
    }

    @Test
    fun `후보가 1개면 해당 linkId를 반환한다`() {
        stubCoordinates()
        val link = moctLink("LINK001", 126.999, 37.0, 127.001, 37.0)
        whenever(moctLinkRepository.findLinksInBoundingBox(any(), any(), any(), any()))
            .thenReturn(listOf(link))

        val result = nearestLinkFinder.findNearestLinkId(100.0, 200.0, 100.0, 210.0)

        result shouldBe LinkMatchResult.Found("LINK001")
    }

    @Test
    fun `접근 방향과 일치하는 링크를 우선 선택한다`() {
        // 사용자가 남쪽(위도 높은 곳)에서 북쪽(위도 낮은 곳)으로 접근
        // approach 방향: (0, -0.01) → 남→북
        stubCoordinates(stationLat = 37.0, stationLon = 127.0, userLat = 37.01, userLon = 127.0)

        // 같은 방향 링크 (남→북, 멀리 있음)
        val sameDir = moctLink("SAME_DIR", 127.0005, 37.001, 127.0005, 36.999)
        // 반대 방향 링크 (북→남, 가까이 있음)
        val oppositeDir = moctLink("OPP_DIR", 127.0001, 36.999, 127.0001, 37.001)

        whenever(moctLinkRepository.findLinksInBoundingBox(any(), any(), any(), any()))
            .thenReturn(listOf(sameDir, oppositeDir))

        val result = nearestLinkFinder.findNearestLinkId(100.0, 200.0, 100.0, 210.0)

        result shouldBe LinkMatchResult.Found("SAME_DIR")
    }

    @Test
    fun `방향 유사 시 가까운 링크를 우선 선택한다`() {
        stubCoordinates(stationLat = 37.0, stationLon = 127.0, userLat = 37.01, userLon = 127.0)

        // 같은 방향이면서 가까운 링크
        val near = moctLink("NEAR", 127.0001, 37.0005, 127.0001, 36.9995)
        // 같은 방향이면서 먼 링크
        val far = moctLink("FAR", 127.005, 37.005, 127.005, 36.995)

        whenever(moctLinkRepository.findLinksInBoundingBox(any(), any(), any(), any()))
            .thenReturn(listOf(far, near))

        val result = nearestLinkFinder.findNearestLinkId(100.0, 200.0, 100.0, 210.0)

        result shouldBe LinkMatchResult.Found("NEAR")
    }

    @Test
    fun `방향 매칭 실패 시 가장 가까운 링크로 fallback한다`() {
        // 사용자가 남→북으로 접근, 모든 링크가 반대 방향(북→남)
        stubCoordinates(stationLat = 37.0, stationLon = 127.0, userLat = 37.01, userLon = 127.0)

        val nearOpp = moctLink("NEAR_OPP", 127.0001, 36.999, 127.0001, 37.001)
        val farOpp = moctLink("FAR_OPP", 127.005, 36.995, 127.005, 37.005)

        whenever(moctLinkRepository.findLinksInBoundingBox(any(), any(), any(), any()))
            .thenReturn(listOf(farOpp, nearOpp))

        val result = nearestLinkFinder.findNearestLinkId(100.0, 200.0, 100.0, 210.0)

        result shouldBe LinkMatchResult.Found("NEAR_OPP")
    }
}
