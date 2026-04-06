package com.kumoh.lbs.gasstation.batch.controller

import com.kumoh.lbs.gasstation.batch.client.OpinetCsvDownloader
import com.kumoh.lbs.gasstation.batch.service.GasStationCsvBatchService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File

private val logger = KotlinLogging.logger {}

@Tag(name = "OPINET 배치", description = "OPINET CSV 수동 다운로드 및 DB 임포트")
@RestController
@RequestMapping("/api/batch/opinet")
class OpinetBatchController(
    private val opinetCsvDownloader: OpinetCsvDownloader,
    private val gasStationCsvBatchService: GasStationCsvBatchService
) {

    companion object {
        private val WORK_DIR = System.getProperty("java.io.tmpdir")
        private const val CSV_FILE_NAME = "opinet_current_price.csv"
    }

    @Operation(summary = "OPINET CSV 수동 임포트", description = "OPINET에서 현재 판매가격 CSV를 다운로드하고 DB에 임포트합니다.")
    @PostMapping("/import")
    fun manualImport(): BatchResultResponse {
        logger.info { "OPINET CSV 수동 배치 시작" }

        val csvBytes = opinetCsvDownloader.downloadCurrentPriceCsv()

        val csvFile = File(WORK_DIR, CSV_FILE_NAME)
        csvFile.writeBytes(csvBytes)
        logger.info { "CSV 저장: ${csvFile.absolutePath} (${csvBytes.size} bytes)" }

        val result = gasStationCsvBatchService.importFromCsv(csvFile.absolutePath, OpinetCsvDownloader.CSV_CHARSET)

        return if (result.unchanged) {
            logger.info { "변경 없음 — DB 업데이트 건너뜀" }
            BatchResultResponse("변경 없음", 0, 0, 0)
        } else {
            logger.info { "배치 완료 — 저장: ${result.saved}, 버려진 행: ${result.dropped}" }
            BatchResultResponse("완료", result.saved, result.badRows, result.geocodeErrors)
        }
    }

    data class BatchResultResponse(
        val status: String,
        val saved: Int,
        val badRows: Int,
        val geocodeErrors: Int
    )
}
