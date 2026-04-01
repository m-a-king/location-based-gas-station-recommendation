package com.kumoh.lbs.gasstation.batch.service

import com.kumoh.lbs.gasstation.batch.domain.BatchMetadata
import com.kumoh.lbs.gasstation.batch.domain.BatchMetadataId
import com.kumoh.lbs.gasstation.batch.repository.BatchMetadataRepository
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class GasStationBatchWriter(
    private val gasStationRepository: GasStationRepository,
    private val batchMetadataRepository: BatchMetadataRepository
) {
    @Transactional
    fun saveBatch(stations: List<GasStation>) {
        gasStationRepository.saveAll(stations)
        logger.debug { "${stations.size}건 저장 완료" }
    }

    fun isUnchanged(source: String, fileName: String, hash: String): Boolean =
        batchMetadataRepository.findById(BatchMetadataId(source, fileName))
            .orElse(null)?.lastHash == hash

    @Transactional
    fun upsertMetadata(source: String, fileName: String, hash: String) {
        val id = BatchMetadataId(source, fileName)
        val now = LocalDateTime.now()
        val existing = batchMetadataRepository.findById(id).orElse(null)
        if (existing != null) {
            existing.lastHash = hash
            existing.updatedAt = now
        } else {
            batchMetadataRepository.save(BatchMetadata(id = id, lastHash = hash, updatedAt = now))
        }
    }
}
