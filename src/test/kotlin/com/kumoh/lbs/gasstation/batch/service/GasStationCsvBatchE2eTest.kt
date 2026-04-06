package com.kumoh.lbs.gasstation.batch.service

import com.kumoh.lbs.TestcontainersConfiguration
import com.kumoh.lbs.gasstation.batch.client.KakaoLocalClient
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import com.kumoh.lbs.geo.Coordinate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import java.nio.charset.StandardCharsets

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class GasStationCsvBatchE2eTest(
    @Autowired val service: GasStationCsvBatchService,
    @Autowired val gasStationRepository: GasStationRepository,
    @Autowired val gasStationPriceRepository: GasStationPriceRepository,
    @MockitoBean val kakaoLocalClient: KakaoLocalClient
) {

    @AfterEach
    fun tearDown() {
        gasStationPriceRepository.deleteAll()
        gasStationRepository.deleteAll()
    }

    @Test
    fun `CSV 임포트 시 주유소와 가격이 모두 DB에 저장된다`() {
        val csv = """
            고유번호,지역,상호,주소,상표,셀프여부,고급휘발유,휘발유,경유,실내등유
            ST001,서울,테스트주유소A,서울 강남구 테헤란로 1,SKE,N,1900,1600,1400,1200
            ST002,서울,테스트주유소B,서울 강남구 테헤란로 2,GSC,Y,,1550,1350,
        """.trimIndent()

        val file = File.createTempFile("batch_e2e_", ".csv")
        file.deleteOnExit()
        file.writeText(csv, StandardCharsets.UTF_8)

        whenever(kakaoLocalClient.resolveCoordinates(any())).thenReturn(Coordinate.Wgs84(37.5, 127.0))

        val result = service.importFromCsv(file.absolutePath, StandardCharsets.UTF_8)

        result.saved shouldBe 2

        // 주유소 저장 확인
        gasStationRepository.count() shouldBe 2

        // 가격 저장 확인
        val st001Prices = gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(listOf("ST001"), FuelType.GASOLINE)
        st001Prices.size shouldBe 1
        st001Prices[0].price shouldBe 1600

        val st001Diesel = gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(listOf("ST001"), FuelType.DIESEL)
        st001Diesel.size shouldBe 1
        st001Diesel[0].price shouldBe 1400

        val st001Premium = gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(listOf("ST001"), FuelType.PREMIUM_GASOLINE)
        st001Premium.size shouldBe 1
        st001Premium[0].price shouldBe 1900

        // ST002는 고급휘발유가 빈 값이므로 저장 안 됨
        val st002Premium = gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(listOf("ST002"), FuelType.PREMIUM_GASOLINE)
        st002Premium.size shouldBe 0

        // ST002 휘발유는 저장됨
        val st002Gasoline = gasStationPriceRepository.findAllByIdStationIdInAndIdFuelType(listOf("ST002"), FuelType.GASOLINE)
        st002Gasoline.size shouldBe 1
        st002Gasoline[0].price shouldBe 1550
    }
}
