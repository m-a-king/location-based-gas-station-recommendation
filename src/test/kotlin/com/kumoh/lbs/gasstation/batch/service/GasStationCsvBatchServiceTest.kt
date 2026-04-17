package com.kumoh.lbs.gasstation.batch.service

import com.kumoh.lbs.gasstation.batch.client.KakaoLocalClient
import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import com.kumoh.lbs.geo.Coordinate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * CSV 배치 서비스 단위 테스트.
 *
 * 모킹 이유:
 * - kakaoLocalClient: 외부 HTTP(Kakao Local Geocoding) — 외부 호출이므로 모킹 허용
 * - batchWriter: argumentCaptor로 "어떤 GasStation 리스트가 어느 배치 경계로 나뉘어 저장 요청됐는지",
 *   "saveMetadata가 호출됐는지/안 됐는지"를 격리 검증하기 위해 모킹한다. DB 저장 결과물만으로는
 *   배치 경계 / 메타데이터 호출 여부를 직접 확인할 수 없다.
 * - gasStationRepository: findAllCoord() 스텁으로 "기존 좌표가 있으면 geocoding을 재호출하지 않는다"를
 *   격리 검증. DB 조합 시나리오는 GasStationCsvBatchE2eTest에서 Testcontainers로 검증한다.
 */
@ExtendWith(MockitoExtension::class)
class GasStationCsvBatchServiceTest {

    @Mock lateinit var kakaoLocalClient: KakaoLocalClient
    @Mock lateinit var batchWriter: GasStationImportWriter
    @Mock lateinit var gasStationRepository: GasStationRepository

    @InjectMocks lateinit var service: GasStationCsvBatchService

    @TempDir lateinit var tempDir: File

    private val charset = StandardCharsets.UTF_8

    // ─── CSV 헬퍼 ─────────────────────────────────────────────────────────────

    private fun csvFile(name: String, content: String): String {
        val file = File(tempDir, name)
        file.writeText(content, charset)
        return file.absolutePath
    }

    private val validHeader = "주유소코드,주유소명,상표,주소,셀프여부"

    private fun validRow(id: String, address: String = "서울시 강남구 테헤란로 1") =
        "$id,테스트주유소,SKE,$address,N"

    // ─── 헤더 검증 ────────────────────────────────────────────────────────────

    @Test
    fun `주소 헤더 누락 시 예외 발생 및 메타데이터 미갱신`() {
        val path = csvFile("no_address.csv", "주유소코드,주유소명\nST001,테스트")

        assertThrows<IllegalStateException> {
            service.importFromCsv(path, charset)
        }

        verify(batchWriter, never()).saveMetadata(any(), any())
    }

    @Test
    fun `주유소코드 헤더 누락 시 예외 발생 및 메타데이터 미갱신`() {
        val path = csvFile("no_id.csv", "주유소명,주소\n테스트,서울시 강남구")

        assertThrows<IllegalStateException> {
            service.importFromCsv(path, charset)
        }

        verify(batchWriter, never()).saveMetadata(any(), any())
    }

    // ─── 전체 validation failure ──────────────────────────────────────────────

    @Test
    fun `모든 행 검증 실패 시에도 메타데이터 기록`() {
        val path = csvFile("no_id_rows.csv", "$validHeader\n,주유소A,SKE,서울 강남,N")

        val result = service.importFromCsv(path, charset)

        result.badRows shouldBe 1
        verify(batchWriter).saveMetadata(any(), any())
    }

    @Test
    fun `모든 행 주소 누락 시에도 메타데이터 기록`() {
        val path = csvFile("no_addr_rows.csv", "$validHeader\nST001,주유소A,SKE,,N")

        val result = service.importFromCsv(path, charset)

        result.badRows shouldBe 1
        verify(batchWriter).saveMetadata(any(), any())
    }

    // ─── quoted comma ─────────────────────────────────────────────────────────

    @Test
    fun `쉼표 포함 주소가 quoted field로 감싸진 경우 정상 파싱된다`() {
        val path = csvFile("quoted.csv",
            "$validHeader\nST001,테스트,SKE,\"서울시 강남구, 테헤란로 1\",N"
        )
        whenever(kakaoLocalClient.resolveCoordinates("서울시 강남구, 테헤란로 1"))
            .thenReturn(Coordinate.Wgs84(37.5, 127.0))

        val result = service.importFromCsv(path, charset)

        result.saved shouldBe 1
        result.badRows shouldBe 0
    }

    @Test
    fun `쉼표 포함 주소를 split 방식으로 파싱하면 컬럼이 밀려 실패해야 하지만 CSV 파서는 성공한다`() {
        // 이 테스트는 quoted comma가 정상 파싱됨을 역으로 검증
        val quotedAddress = "서울시 중구, 을지로 100"
        val path = csvFile("quoted_verify.csv",
            "$validHeader\nST002,주유소B,GSC,\"$quotedAddress\",Y"
        )
        whenever(kakaoLocalClient.resolveCoordinates(quotedAddress)).thenReturn(Coordinate.Wgs84(37.5, 126.9))

        val result = service.importFromCsv(path, charset)

        result.saved shouldBe 1
        result.geocodeErrors shouldBe 0
    }

    // ─── geocode 실패 격리 ────────────────────────────────────────────────────

    @Test
    fun `geocode null 반환 시 geocodeErrors 카운트 기록 및 메타데이터 저장`() {
        val path = csvFile("geocode_null.csv",
            "$validHeader\n${validRow("ST001")}"
        )
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(null)

        val result = service.importFromCsv(path, charset)

        result.geocodeErrors shouldBe 1
        result.saved shouldBe 0
        verify(batchWriter).saveMetadata(any(), any())
    }

    @Test
    fun `geocode 예외 발생 시 GeocodeFailed로 처리하고 배치 중단 안 한다`() {
        val path = csvFile("geocode_ex.csv",
            "$validHeader\n${validRow("ST001")}\n${validRow("ST002")}"
        )
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenThrow(RuntimeException("API timeout"))

        val result = service.importFromCsv(path, charset)

        result.geocodeErrors shouldBe 2
        result.saved shouldBe 0
        verify(batchWriter).saveMetadata(any(), any())
    }

    @Test
    fun `일부 행만 geocode 실패해도 성공한 행은 저장되고 메타데이터도 갱신된다`() {
        val path = csvFile("partial.csv",
            "$validHeader\n${validRow("ST001")}\n${validRow("ST002", "주소없음")}"
        )
        whenever(kakaoLocalClient.resolveCoordinates("서울시 강남구 테헤란로 1")).thenReturn(Coordinate.Wgs84(37.5, 127.0))
        whenever(kakaoLocalClient.resolveCoordinates("주소없음")).thenReturn(null)

        val result = service.importFromCsv(path, charset)

        result.saved shouldBe 1
        result.geocodeErrors shouldBe 1
        verify(batchWriter).saveMetadata(any(), any())
    }

    // ─── 변환 결과 검증 ──────────────────────────────────────────────────────

    @Test
    fun `name 컬럼이 비어있으면 stationId로 대체된다`() {
        val path = csvFile("no_name.csv", "$validHeader\nST001,,SKE,서울 강남,N")
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        service.importFromCsv(path, charset)

        val captor = argumentCaptor<List<GasStation>>()
        verify(batchWriter).saveBatch(captor.capture(), any())
        val saved = captor.firstValue[0]
        saved.name shouldBe "ST001"
    }

    @Test
    fun `인식 가능한 brand 코드는 한글 브랜드명으로 변환된다`() {
        val path = csvFile("brand_ske.csv", "$validHeader\nST001,주유소A,SKE,서울 강남,N")
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        service.importFromCsv(path, charset)

        val captor = argumentCaptor<List<GasStation>>()
        verify(batchWriter).saveBatch(captor.capture(), any())
        val saved = captor.firstValue[0]
        saved.brand shouldBe "SK에너지"
    }

    @Test
    fun `인식 불가 brand는 기타로 처리된다`() {
        val path = csvFile("brand_unknown.csv", "$validHeader\nST001,주유소A,UNKNOWN,서울 강남,N")
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        service.importFromCsv(path, charset)

        val captor = argumentCaptor<List<GasStation>>()
        verify(batchWriter).saveBatch(captor.capture(), any())
        val saved = captor.firstValue[0]
        saved.brand shouldBe "기타"
    }

    @Test
    fun `셀프여부가 Y이면 isSelf=true다`() {
        val path = csvFile("self_y.csv", "$validHeader\nST001,주유소A,SKE,서울 강남,Y")
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        service.importFromCsv(path, charset)

        val captor = argumentCaptor<List<GasStation>>()
        verify(batchWriter).saveBatch(captor.capture(), any())
        captor.firstValue[0].isSelf shouldBe true
    }

    @Test
    fun `셀프여부가 셀프이면 isSelf=true다`() {
        val path = csvFile("self_text.csv", "$validHeader\nST001,주유소A,SKE,서울 강남,셀프")
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        service.importFromCsv(path, charset)

        val captor = argumentCaptor<List<GasStation>>()
        verify(batchWriter).saveBatch(captor.capture(), any())
        captor.firstValue[0].isSelf shouldBe true
    }

    @Test
    fun `기존 좌표가 있으면 geocoding을 호출하지 않는다`() {
        val path = csvFile("existing_coord.csv", "$validHeader\n${validRow("ST001")}")
        whenever(gasStationRepository.findAllCoord()).thenReturn(listOf(
            object : com.kumoh.lbs.gasstation.repository.StationCoord {
                override val id = "ST001"
                override val latitude = 37.5
                override val longitude = 127.0
            }
        ))

        service.importFromCsv(path, charset)

        verify(kakaoLocalClient, never()).resolveCoordinates(any())
    }

    @Test
    fun `50건은 saveBatch 1번 호출된다`() {
        val rows = (1..50).joinToString("\n") { validRow("ST$it") }
        val path = csvFile("batch50.csv", "$validHeader\n$rows")
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        service.importFromCsv(path, charset)

        verify(batchWriter, times(1)).saveBatch(any(), any())
    }

    @Test
    fun `51건은 saveBatch가 2번 호출되고 총 51건이 저장된다`() {
        val rows = (1..51).joinToString("\n") { validRow("ST$it") }
        val path = csvFile("batch51.csv", "$validHeader\n$rows")
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        val result = service.importFromCsv(path, charset)

        // saveBatch가 50건 + 1건으로 2번 나뉘어 호출됨
        // 서비스가 가변 리스트를 재사용하므로 배치 크기는 result.saved로 간접 검증
        verify(batchWriter, times(2)).saveBatch(any(), any())
        result.saved shouldBe 51
    }

    @Test
    fun `UNI_ID, NEW_ADR, SELF_YN 헤더도 인식한다`() {
        val path = csvFile("opinet_header.csv",
            "UNI_ID,OS_NM,POLL_DIV_NM,NEW_ADR,SELF_YN\nST001,주유소A,SKE,서울 강남,Y"
        )
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        val result = service.importFromCsv(path, charset)

        result.saved shouldBe 1
    }

    @Test
    fun `OPINET 현재 판매가격 CSV 헤더 형식을 인식한다`() {
        val path = csvFile("opinet_current_price.csv",
            "고유번호,지역,상호,주소,상표,셀프여부,고급휘발유,휘발유,경유,실내등유\n" +
                "ST001,서울,테스트주유소,서울 강남구 테헤란로 1,SKE,N,1900,1600,1400,1200"
        )
        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        val result = service.importFromCsv(path, charset)

        result.saved shouldBe 1
    }

    // ─── 스킵 정책 ───────────────────────────────────────────────────────────

    @Test
    fun `이미 처리된 파일은 isUnchanged가 true면 스킵 반환`() {
        val path = csvFile("processed.csv", "$validHeader\n${validRow("ST001")}")
        whenever(batchWriter.isUnchanged(any(), any())).thenReturn(true)

        val result = service.importFromCsv(path, charset)

        result.unchanged shouldBe true
        verify(batchWriter, never()).saveBatch(any(), any())
    }
}
