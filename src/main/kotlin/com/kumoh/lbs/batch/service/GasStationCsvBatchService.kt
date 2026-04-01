package com.kumoh.lbs.batch.service

import com.kumoh.lbs.batch.domain.BatchMetadata
import com.kumoh.lbs.batch.repository.BatchMetadataRepository
import com.kumoh.lbs.common.client.KakaoLocalClient
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.StationType
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * OPINET CSV 파일에서 주유소 정보를 읽고,
 * 주소를 카카오 API로 좌표 변환하여 DB에 저장한다.
 *
 * 지원하는 CSV 컬럼 형식 (헤더 기준):
 *   지역, 상표, 주유소코드, 주유소명, 주소, 전화번호, 셀프여부, ...
 *
 * 인코딩: 기본 EUC-KR (OPINET 기본 인코딩). UTF-8 파일이면 charset 파라미터로 지정.
 */
@Service
class GasStationCsvBatchService(
    private val kakaoLocalClient: KakaoLocalClient,
    private val gasStationRepository: GasStationRepository,
    private val batchMetadataRepository: BatchMetadataRepository
) {
    companion object {
        private const val BATCH_SIZE = 50

        // OPINET CSV 컬럼 헤더명 후보 목록
        private val STATION_ID_COLS = setOf("주유소코드", "UNI_ID", "주유소ID")
        private val NAME_COLS = setOf("주유소명", "OS_NM", "상호명")
        private val BRAND_COLS = setOf("상표", "POLL_DIV_NM", "브랜드")
        private val ADDRESS_COLS = setOf("주소", "NEW_ADR", "VAN_ADR", "도로명주소", "지번주소")
        private val SELF_COLS = setOf("셀프여부", "SELF_YN")
    }

    /**
     * @param filePath OPINET CSV 파일 경로
     * @param charset  파일 인코딩 (기본 EUC-KR)
     */
    fun importFromCsv(
        filePath: String,
        charset: Charset = Charset.forName("EUC-KR")
    ): ImportResult {
        val file = File(filePath)
        require(file.exists()) { "CSV 파일을 찾을 수 없습니다: $filePath" }

        val fileHash = file.md5Hash()
        val metadata = batchMetadataRepository.findById(file.name).orElse(null)

        if (metadata?.lastHash == fileHash) {
            logger.info { "파일 변경 없음 — 건너뜀: ${file.name}" }
            return ImportResult(skipped = true)
        }

        logger.info { "CSV 배치 시작: ${file.name}" }
        var success = 0
        var failed = 0

        file.bufferedReader(charset).use { reader ->
            val headerLine = reader.readLine()?.trimBom()
                ?: return ImportResult(success = 0, failed = 0).also {
                    logger.warn { "빈 파일: ${file.name}" }
                }

            val headers = headerLine.split(",").map { it.trim() }
            val idx = ColumnIndex.from(headers)
            logger.info { "헤더 인식: $headers" }

            if (idx.stationId == -1) {
                error("주유소코드 컬럼을 찾을 수 없습니다. 헤더: $headers\n" +
                        "지원 컬럼명: $STATION_ID_COLS")
            }
            if (idx.address == -1) {
                error("주소 컬럼을 찾을 수 없습니다. 헤더: $headers\n" +
                        "지원 컬럼명: $ADDRESS_COLS")
            }

            val batch = mutableListOf<GasStation>()

            reader.lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    val cols = line.split(",").map { it.trim() }

                    val stationId = cols.getOrNull(idx.stationId)?.takeIf { it.isNotBlank() }
                    if (stationId == null) {
                        logger.warn { "주유소코드 없는 행 건너뜀: $line" }
                        failed++
                        return@forEach
                    }

                    val address = cols.getOrNull(idx.address)?.takeIf { it.isNotBlank() }
                    if (address == null) {
                        logger.warn { "주소 없는 행 건너뜀 (id=$stationId)" }
                        failed++
                        return@forEach
                    }

                    val coords = kakaoLocalClient.geocode(address)
                    if (coords == null) {
                        failed++
                        return@forEach
                    }

                    val (latitude, longitude) = coords
                    val brandRaw = cols.getOrNull(idx.brand) ?: ""
                    batch.add(
                        GasStation(
                            id = stationId,
                            name = cols.getOrNull(idx.name)?.takeIf { it.isNotBlank() } ?: stationId,
                            brand = BrandName.from(brandRaw),
                            address = address,
                            isSelf = cols.getOrNull(idx.isSelf)?.trim() == "Y" ||
                                     cols.getOrNull(idx.isSelf)?.trim() == "셀프",
                            type = StationType.GAS_STATION,
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                    success++

                    if (batch.size >= BATCH_SIZE) {
                        saveBatch(batch)
                        batch.clear()
                    }
                }

            if (batch.isNotEmpty()) {
                saveBatch(batch)
            }
        }

        updateMetadata(metadata, file.name, fileHash)
        logger.info { "CSV 배치 완료 — 성공: $success, 실패: $failed" }

        return ImportResult(success = success, failed = failed)
    }

    @Transactional
    fun saveBatch(stations: List<GasStation>) {
        gasStationRepository.saveAll(stations)
        logger.debug { "${stations.size}건 저장 완료" }
    }

    @Transactional
    fun updateMetadata(existing: BatchMetadata?, fileName: String, hash: String) {
        val now = LocalDateTime.now()
        batchMetadataRepository.save(
            if (existing != null) {
                existing.lastHash = hash
                existing.updatedAt = now
                existing
            } else {
                BatchMetadata(fileName = fileName, lastHash = hash, updatedAt = now)
            }
        )
    }

    private fun File.md5Hash(): String {
        val digest = MessageDigest.getInstance("MD5")
        inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // BOM(Byte Order Mark) 제거
    private fun String.trimBom() = removePrefix("\uFEFF")

    private data class ColumnIndex(
        val stationId: Int,
        val name: Int,
        val brand: Int,
        val address: Int,
        val isSelf: Int
    ) {
        companion object {
            fun from(headers: List<String>): ColumnIndex {
                fun find(candidates: Set<String>) =
                    headers.indexOfFirst { it in candidates }

                return ColumnIndex(
                    stationId = find(STATION_ID_COLS),
                    name = find(NAME_COLS),
                    brand = find(BRAND_COLS),
                    address = find(ADDRESS_COLS),
                    isSelf = find(SELF_COLS)
                )
            }
        }
    }
}

data class ImportResult(
    val success: Int = 0,
    val failed: Int = 0,
    val skipped: Boolean = false
)

/**
 * OPINET 브랜드 코드 → 한국어 브랜드명 변환.
 * 이미 한국어인 경우 그대로 반환.
 */
private object BrandName {
    private val codeMap = mapOf(
        "SKE" to "SK에너지",
        "GSC" to "GS칼텍스",
        "HDO" to "현대오일뱅크",
        "SOL" to "S-OIL",
        "RTO" to "자영",
        "RTX" to "알뜰(자영)",
        "NHO" to "NH에너지",
        "ETC" to "기타",
        "E1G" to "E1",
        "SKG" to "SK가스"
    )

    fun from(raw: String): String = codeMap[raw.trim().uppercase()] ?: raw.ifBlank { "기타" }
}
