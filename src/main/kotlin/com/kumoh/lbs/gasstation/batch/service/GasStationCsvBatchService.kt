package com.kumoh.lbs.gasstation.batch.service

import com.kumoh.lbs.gasstation.batch.client.KakaoLocalClient
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.StationType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

@Service
class GasStationCsvBatchService(
    private val kakaoLocalClient: KakaoLocalClient,
    private val batchWriter: GasStationBatchWriter
) {
    companion object {
        private const val BATCH_SIZE = 50

        private val STATION_ID_COLS = setOf("주유소코드", "UNI_ID", "주유소ID")
        private val NAME_COLS = setOf("주유소명", "OS_NM", "상호명")
        private val BRAND_COLS = setOf("상표", "POLL_DIV_NM", "브랜드")
        private val ADDRESS_COLS = setOf("주소", "NEW_ADR", "VAN_ADR", "도로명주소", "지번주소")
        private val SELF_COLS = setOf("셀프여부", "SELF_YN")
    }

    fun importFromCsv(
        filePath: String,
        charset: Charset = Charset.forName("EUC-KR"),
        source: String = "manual"
    ): ImportResult {
        val file = File(filePath)
        require(file.exists()) { "CSV 파일을 찾을 수 없습니다: $filePath" }

        val fileHash = file.md5Hash()
        if (batchWriter.isUnchanged(source, file.name, fileHash)) {
            logger.info { "파일 변경 없음 — 건너뜀: ${file.name}" }
            return ImportResult(skipped = true)
        }

        logger.info { "CSV 배치 시작: ${file.name}" }
        val result = processCsv(file, charset)

        // 한 건이라도 성공했거나 완전히 빈 파일만 처리 완료로 기록
        // 전부 실패(검증 or 지오코딩)면 다음 실행에서 재시도하도록 메타데이터 갱신 보류
        if (result.success > 0 || result.failed == 0) {
            batchWriter.upsertMetadata(source, file.name, fileHash)
        } else {
            logger.warn { "전체 실패 (성공: 0, 실패: ${result.failed}) — 메타데이터 갱신 보류 (다음 실행에서 재시도)" }
        }

        logger.info {
            "CSV 배치 완료 — 성공: ${result.success}, " +
            "검증실패: ${result.validationFailed}, 지오코딩실패: ${result.geocodeFailed}"
        }
        return result
    }

    private fun processCsv(file: File, charset: Charset): ImportResult {
        var success = 0
        var validationFailed = 0
        var geocodeFailed = 0
        val batch = mutableListOf<GasStation>()

        file.bufferedReader(charset).use { reader ->
            val csvParser = CSV_FORMAT.parse(reader)
            val cols = ColumnNames.from(csvParser.headerNames)
            logger.info { "헤더 인식: ${csvParser.headerNames.map { it.trimStart('\uFEFF') }}" }

            csvParser.forEachIndexed { index, record ->
                val rowNum = index + 2  // 1-based, header is row 1
                when (val result = toGasStation(record, cols, rowNum)) {
                    is RowResult.Success -> {
                        batch += result.station
                        success++
                        if (batch.size >= BATCH_SIZE) {
                            batchWriter.saveBatch(batch)
                            batch.clear()
                        }
                    }
                    is RowResult.ValidationFailure -> validationFailed++
                    is RowResult.GeocodeFailed -> geocodeFailed++
                }
            }

            if (batch.isNotEmpty()) batchWriter.saveBatch(batch)
        }

        return ImportResult(success = success, validationFailed = validationFailed, geocodeFailed = geocodeFailed)
    }

    private fun toGasStation(record: CSVRecord, cols: ColumnNames, rowNum: Int): RowResult {
        val stationId = record.get(cols.stationId).takeIf { it.isNotBlank() }
            ?: return RowResult.ValidationFailure("주유소코드 없음")
                .also { logger.warn { "[${rowNum}행] 주유소코드 없는 행 건너뜀" } }

        val address = record.get(cols.address).takeIf { it.isNotBlank() }
            ?: return RowResult.ValidationFailure("주소 없음")
                .also { logger.warn { "[${rowNum}행] 주소 없는 행 건너뜀 (id=$stationId)" } }

        val coords = try {
            kakaoLocalClient.geocode(address)
        } catch (e: Exception) {
            logger.warn { "[${rowNum}행] 지오코딩 예외 (id=$stationId): ${e.message}" }
            return RowResult.GeocodeFailed(stationId)
        } ?: run {
            logger.warn { "[${rowNum}행] 지오코딩 결과 없음 (id=$stationId, address=$address)" }
            return RowResult.GeocodeFailed(stationId)
        }

        val (latitude, longitude) = coords
        return RowResult.Success(
            GasStation(
                id = stationId,
                name = cols.name?.let { record.get(it) }?.takeIf { it.isNotBlank() } ?: stationId,
                brand = Brand.from(cols.brand?.let { record.get(it) } ?: "").displayName,
                address = address,
                isSelf = cols.isSelf?.let { record.get(it) }.let { it == "Y" || it == "셀프" },
                type = StationType.GAS_STATION,
                latitude = latitude,
                longitude = longitude
            )
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

    // BOM이 있는 경우 첫 번째 헤더명에 포함될 수 있어 trimStart('\uFEFF')로 비교
    private data class ColumnNames(
        val stationId: String,
        val name: String?,
        val brand: String?,
        val address: String,
        val isSelf: String?
    ) {
        companion object {
            fun from(headers: List<String>): ColumnNames {
                val normalized = headers.map { it.trimStart('\uFEFF') }
                fun find(candidates: Set<String>) =
                    headers.firstOrNull { it.trimStart('\uFEFF') in candidates }

                val stationId = find(STATION_ID_COLS)
                    ?: error("주유소코드 컬럼을 찾을 수 없습니다. 헤더: $normalized\n지원 컬럼명: $STATION_ID_COLS")
                val address = find(ADDRESS_COLS)
                    ?: error("주소 컬럼을 찾을 수 없습니다. 헤더: $normalized\n지원 컬럼명: $ADDRESS_COLS")

                return ColumnNames(
                    stationId = stationId,
                    name = find(NAME_COLS),
                    brand = find(BRAND_COLS),
                    address = address,
                    isSelf = find(SELF_COLS)
                )
            }
        }
    }

    private sealed interface RowResult {
        data class Success(val station: GasStation) : RowResult
        data class ValidationFailure(val reason: String) : RowResult
        data class GeocodeFailed(val stationId: String) : RowResult
    }
}

private val CSV_FORMAT: CSVFormat = CSVFormat.DEFAULT.builder()
    .setHeader()
    .setSkipHeaderRecord(true)
    .setTrim(true)
    .setIgnoreEmptyLines(true)
    .build()

data class ImportResult(
    val success: Int = 0,
    val validationFailed: Int = 0,
    val geocodeFailed: Int = 0,
    val skipped: Boolean = false
) {
    val failed: Int get() = validationFailed + geocodeFailed
}

private enum class Brand(val displayName: String) {
    SKE("SK에너지"),
    GSC("GS칼텍스"),
    HDO("현대오일뱅크"),
    SOL("S-OIL"),
    RTO("자영"),
    RTX("알뜰(자영)"),
    NHO("NH에너지"),
    E1G("E1"),
    SKG("SK가스"),
    ETC("기타");

    companion object {
        fun from(raw: String): Brand {
            val trimmed = raw.trim()
            return entries.firstOrNull { it.name == trimmed.uppercase() }
                ?: entries.firstOrNull { it.displayName == trimmed }
                ?: ETC
        }
    }
}
