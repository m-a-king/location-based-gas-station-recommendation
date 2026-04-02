package com.kumoh.lbs.gasstation.batch.service

import com.kumoh.lbs.gasstation.batch.domain.OpinetCsvHistory
import com.kumoh.lbs.gasstation.batch.repository.OpinetCsvHistoryRepository
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class GasStationImportWriter(
    private val gasStationRepository: GasStationRepository,
    private val historyRepository: OpinetCsvHistoryRepository
) {
    @Transactional
    fun saveBatch(stations: List<GasStation>) {
        gasStationRepository.saveAll(stations)
        logger.debug { "${stations.size}건 저장 완료" }
    }

    fun isUnchanged(fileName: String, hash: String): Boolean =
        historyRepository.findByFileName(fileName)?.lastHash == hash

    @Transactional
    fun saveMetadata(fileName: String, hash: String) {
        val existing = historyRepository.findByFileName(fileName)
        if (existing != null) {
            existing.lastHash = hash
        } else {
            historyRepository.save(OpinetCsvHistory(fileName = fileName, lastHash = hash))
        }
    }
}
