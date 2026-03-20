package com.kumoh.lbs.service

import com.kumoh.lbs.client.ItsClient
import com.kumoh.lbs.client.TrafficLink
import com.kumoh.lbs.domain.Coordinate
import com.kumoh.lbs.service.NearestLinkFinder.LinkMatchResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class TrafficSpeedFinderTest {

    @Mock
    lateinit var itsClient: ItsClient

    @Mock
    lateinit var nearestLinkFinder: NearestLinkFinder

    @InjectMocks
    lateinit var trafficSpeedFinder: TrafficSpeedFinder

    private fun stationCoordinate() = Coordinate.fromWgs84(Coordinate.Wgs84(37.0, 127.0))

    @Test
    fun `매칭된 linkId의 ITS 속도를 반환한다`() {
        val location = stationCoordinate()
        whenever(nearestLinkFinder.findNearestLinkId(any()))
            .thenReturn(LinkMatchResult.Found("MATCHED_LINK"))
        whenever(itsClient.searchTrafficLinks(any()))
            .thenReturn(
                listOf(
                    trafficLink("OTHER_LINK", "10.0"),
                    trafficLink("MATCHED_LINK", "50.0")
                )
            )

        val speed = trafficSpeedFinder.findAt(location)

        speed shouldBe 50.0
    }

    @Test
    fun `ITS 응답에 매칭 linkId가 없으면 최저 속도로 fallback한다`() {
        val location = stationCoordinate()
        whenever(nearestLinkFinder.findNearestLinkId(any()))
            .thenReturn(LinkMatchResult.Found("MISSING_LINK"))
        whenever(itsClient.searchTrafficLinks(any()))
            .thenReturn(
                listOf(
                    trafficLink("LINK_A", "30.0"),
                    trafficLink("LINK_B", "20.0")
                )
            )

        val speed = trafficSpeedFinder.findAt(location)

        speed shouldBe 20.0
    }

    @Test
    fun `DB에서 링크를 못 찾으면 최저 속도로 fallback한다`() {
        val location = stationCoordinate()
        whenever(nearestLinkFinder.findNearestLinkId(any()))
            .thenReturn(LinkMatchResult.NotFound)
        whenever(itsClient.searchTrafficLinks(any()))
            .thenReturn(
                listOf(
                    trafficLink("LINK_A", "40.0"),
                    trafficLink("LINK_B", "15.0")
                )
            )

        val speed = trafficSpeedFinder.findAt(location)

        speed shouldBe 15.0
    }

    @Test
    fun `ITS 응답이 비어있으면 0을 반환한다`() {
        val location = stationCoordinate()
        whenever(nearestLinkFinder.findNearestLinkId(any()))
            .thenReturn(LinkMatchResult.NotFound)
        whenever(itsClient.searchTrafficLinks(any()))
            .thenReturn(emptyList())

        val speed = trafficSpeedFinder.findAt(location)

        speed shouldBe 0.0
    }

    private fun trafficLink(linkId: String, speed: String) = TrafficLink(
        roadName = "테스트도로",
        linkId = linkId,
        speed = speed,
        travelTime = "60",
        createdDate = "2026-03-17"
    )
}
