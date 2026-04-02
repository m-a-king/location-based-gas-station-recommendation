package com.kumoh.lbs.gasstation.batch.service

import com.kumoh.lbs.gasstation.batch.client.KakaoLocalClient
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.domain.StationType
import com.kumoh.lbs.geo.Coordinate
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
    private val batchWriter: GasStationBatchWriter,
    private val gasStationRepository: GasStationRepository
) {
    companion object {
        private const val BATCH_SIZE = 50

        private val STATION_ID_COLS = setOf("주유소코드", "UNI_ID", "주유소ID")
        private val NAME_COLS = setOf("주유소명", "OS_NM", "상호명")
        private val BRAND_COLS = setOf("상표", "POLL_DIV_NM", "브랜드")
        private val ADDRESS_COLS = setOf("주소", "NEW_ADR", "VAN_ADR", "도로명주소", "지번주소")
        private val SELF_COLS = setOf("셀프여부", "SELF_YN")
        private val SELF_VALUES = setOf("Y", "셀프")
        private const val BOM = '\uFEFF'
    }

    fun importFromCsv(
        filePath: String,
        charset: Charset = Charset.forName("EUC-KR")
    ): ImportResult {
        val file = File(filePath)
        require(file.exists()) { "CSV 파일을 찾을 수 없습니다: $filePath" }

        val fileHash = file.md5Hash()
        if (batchWriter.isUnchanged(file.name, fileHash)) {
            logger.info { "파일 변경 없음 — 건너뜀: ${file.name}" }
            return ImportResult(unchanged = true)
        }

        logger.info { "CSV 배치 시작: ${file.name}" }
        val result = processCsv(file, charset)

        batchWriter.saveMetadata(file.name, fileHash)

        logger.info { "CSV 배치 완료 — 저장: ${result.saved}, 불량행: ${result.badRows}, 좌표오류: ${result.geocodeErrors}" }
        return result
    }

    private fun processCsv(file: File, charset: Charset): ImportResult {
        var saved = 0
        var badRows = 0
        var geocodeErrors = 0
        val batch = mutableListOf<GasStation>()

        val existingCoords: Map<String, Coordinate.Wgs84> = gasStationRepository.findAllCoord()
            .associate { it.id to Coordinate.Wgs84(it.latitude, it.longitude) }
        logger.info { "기존 좌표 로드: ${existingCoords.size}건" }

        file.bufferedReader(charset).use { reader ->
            val csvParser = CSV_FORMAT.parse(reader)
            val cols = ColumnNames.from(csvParser.headerNames)
            logger.info { "헤더 인식: ${csvParser.headerNames.map { it.trimStart(BOM) }}" }

            csvParser.forEachIndexed { index, record ->
                val rowNum = index + 2  // 1-based, header is row 1
                when (val result = toGasStation(record, cols, rowNum, existingCoords)) {
                    is RowResult.Success -> {
                        batch += result.station
                        saved++
                        if (batch.size >= BATCH_SIZE) {
                            batchWriter.saveBatch(batch)
                            batch.clear()
                        }
                    }
                    is RowResult.ValidationFailure -> badRows++
                    is RowResult.GeocodeFailed -> geocodeErrors++
                }
            }

            if (batch.isNotEmpty()) batchWriter.saveBatch(batch)
        }

        return ImportResult(saved = saved, badRows = badRows, geocodeErrors = geocodeErrors)
    }

    private fun toGasStation(
        record: CSVRecord,
        cols: ColumnNames,
        rowNum: Int,
        existingCoords: Map<String, Coordinate.Wgs84>
    ): RowResult {
        val stationId = record.get(cols.stationId).takeIf { it.isNotBlank() }
            ?: return RowResult.ValidationFailure("주유소코드 없음")
                .also { logger.warn { "[${rowNum}행] 주유소코드 없는 행 건너뜀" } }

        val address = record.get(cols.address).takeIf { it.isNotBlank() }
            ?: return RowResult.ValidationFailure("주소 없음")
                .also { logger.warn { "[${rowNum}행] 주소 없는 행 건너뜀 (id=$stationId)" } }

        val coords = existingCoords[stationId] ?: try {
            kakaoLocalClient.resolveCoordinates(address)
        } catch (e: Exception) {
            logger.warn { "[${rowNum}행] 지오코딩 예외 (id=$stationId): ${e.message}" }
            return RowResult.GeocodeFailed(stationId)
        } ?: run {
            logger.warn { "[${rowNum}행] 지오코딩 결과 없음 (id=$stationId, address=$address)" }
            return RowResult.GeocodeFailed(stationId)
        }

        return RowResult.Success(
            GasStation(
                id = stationId,
                name = cols.name?.let { record.get(it) }?.takeIf { it.isNotBlank() } ?: stationId,
                brand = Brand.from(cols.brand?.let { record.get(it) } ?: "").displayName,
                address = address,
                isSelf = cols.isSelf?.let { record.get(it) } in SELF_VALUES,
                type = StationType.GAS_STATION,
                latitude = coords.latitude,
                longitude = coords.longitude
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

    // BOM이 있는 경우 첫 번째 헤더명에 포함될 수 있어 trimStart(BOM)로 비교
    private data class ColumnNames(
        val stationId: String,
        val name: String?,
        val brand: String?,
        val address: String,
        val isSelf: String?
    ) {
        companion object {
            fun from(headers: List<String>): ColumnNames {
                val normalized = headers.map { it.trimStart(BOM) }
                fun find(candidates: Set<String>) =
                    normalized.indexOfFirst { it in candidates }.takeIf { it >= 0 }?.let { headers[it] }

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
    val saved: Int = 0,
    val badRows: Int = 0,
    val geocodeErrors: Int = 0,
    val unchanged: Boolean = false
) {
    val dropped: Int get() = badRows + geocodeErrors
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
