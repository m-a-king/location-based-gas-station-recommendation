package com.kumoh.lbs.client

import com.kumoh.lbs.config.ItsProperties
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * ITS API 실제 호출 테스트.
 * API 키가 필요하므로 @Disabled — 수동으로 실행.
 *
 * 실행 방법: @Disabled 주석 해제 후
 * ./gradlew test --tests "com.kumoh.lbs.client.ItsClientManualTest"
 */
class ItsClientManualTest {

    @Test
    @Disabled("수동 실행용 — API 키 필요")
    fun `서울 시청 부근 실시간 교통 정보 조회`() {
        // .env.local에서 직접 읽기
        val envFile = java.io.File(".env.local")
        val apiKey = envFile.readLines()
            .first { it.startsWith("ITS_API_KEY=") }
            .substringAfter("=")

        val itsRestClient = RestClient.builder()
            .baseUrl(ItsClient.BASE_URL)
            .build()
        val itsClient = ItsClient(itsRestClient, ItsProperties(apiKey))

        // 서울 시청 부근 약 200m 영역
        val links = itsClient.searchTrafficLinks(
            minX = 126.976,
            maxX = 126.980,
            minY = 37.565,
            maxY = 37.568
        )

        println("=== ITS API 응답: ${links.size}건 ===")
        links.forEach { link ->
            println("도로: ${link.roadName}, linkId: ${link.linkId}, 속도: ${link.speed}km/h, 생성일시: ${link.createdDate}")
        }

        // 기본 검증: 서울 시청 부근이면 도로가 있어야 함
        assert(links.isNotEmpty()) { "서울 시청 부근에 교통 데이터가 없습니다" }
    }
}
