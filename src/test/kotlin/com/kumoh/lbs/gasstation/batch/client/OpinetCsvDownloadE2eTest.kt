package com.kumoh.lbs.gasstation.batch.client

import com.kumoh.lbs.infra.OpinetProperties
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import org.apache.commons.csv.CSVFormat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.Charset

/**
 * OPINET 실제 외부 호출 E2E 테스트.
 *
 * - 기본 @Disabled: CI에서는 건너뜀
 * - 로컬에서 수동 실행: IDE에서 @Disabled 주석 해제 후 실행
 * - 환경변수 OPINET_USER_ID, OPINET_PASSWORD 설정 필요
 */
@Disabled("외부 OPINET 서버 호출 — 수동 실행 전용")
class OpinetCsvDownloadE2eTest {

    private val downloader = OpinetCsvDownloader(
        OpinetProperties(
            apiKey = env("OPINET_API_KEY", "F260316467"),
            userId = env("OPINET_USER_ID"),
            password = env("OPINET_PASSWORD")
        )
    )

    private val csvCharset: Charset = OpinetCsvDownloader.CSV_CHARSET

    private val expectedHeaders = setOf("고유번호", "지역", "상호", "주소", "상표", "셀프여부")

    @Test
    fun `OPINET CSV 다운로드 및 헤더 파싱 검증`() {
        val bytes = downloader.downloadCurrentPriceCsv()
        bytes.size shouldBeGreaterThan 0

        val csvFile = File.createTempFile("opinet_e2e_", ".csv")
        csvFile.deleteOnExit()
        csvFile.writeBytes(bytes)

        val text = bytes.toString(csvCharset)
        val firstLine = text.lineSequence().first()
        println("CSV 헤더: $firstLine")

        val reader = csvFile.bufferedReader(csvCharset)
        val csvFormat = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build()

        val parser = csvFormat.parse(reader)
        val headers = parser.headerNames.map { it.trimStart('\uFEFF') }
        println("파싱된 헤더: $headers")

        expectedHeaders.forEach { expected ->
            val found = headers.any { it == expected }
            found shouldBe true
        }

        val records = parser.records
        records.size shouldBeGreaterThan 0
        println("총 ${records.size}행")

        val firstRecord = records.first()
        val stationId = firstRecord.get(headers.first { it == "고유번호" })
        stationId.shouldNotBeEmpty()
        println("첫 번째 주유소: id=${stationId}, 상호=${firstRecord.get("상호")}, 주소=${firstRecord.get("주소")}")

        reader.close()
    }

    private fun env(key: String, default: String? = null): String =
        System.getenv(key) ?: default ?: error("환경변수 $key 가 설정되지 않았습니다")
}
