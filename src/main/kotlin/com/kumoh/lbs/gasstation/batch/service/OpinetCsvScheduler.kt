package com.kumoh.lbs.gasstation.batch.service

import com.kumoh.lbs.gasstation.batch.client.OpinetCsvDownloader
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File

private val logger = KotlinLogging.logger {}

@Component
class OpinetCsvScheduler(
    private val opinetCsvDownloader: OpinetCsvDownloader,
    private val gasStationCsvBatchService: GasStationCsvBatchService
) {
    companion object {
        private val WORK_DIR = System.getProperty("java.io.tmpdir")
        private const val CSV_FILE_NAME = "opinet_current_price.csv"
    }

    @Scheduled(cron = "0 0 2 * * *")
    fun downloadAndImport() {
        logger.info { "OPINET CSV 배치 시작" }

        val csvBytes = try {
            opinetCsvDownloader.downloadCurrentPriceCsv()
        } catch (e: Exception) {
            logger.error { "OPINET CSV 다운로드 실패: ${e.message}" }
            return
        }

        val csvFile = File(WORK_DIR, CSV_FILE_NAME)
        csvFile.writeBytes(csvBytes)
        logger.info { "CSV 저장: ${csvFile.absolutePath} (${csvBytes.size} bytes)" }

        val result = gasStationCsvBatchService.importFromCsv(csvFile.absolutePath, OpinetCsvDownloader.CSV_CHARSET)

        when {
            result.unchanged -> logger.info { "변경 없음 — DB 업데이트 건너뜀" }
            else -> logger.info { "배치 완료 — 저장: ${result.saved}, 버려진행: ${result.dropped}" }
        }
    }
}
