package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.gasstation.batch.repository.OpinetCsvHistoryRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneId

@RestController
@RequestMapping("/api/gas-stations")
class GasStationDataController(
    private val historyRepository: OpinetCsvHistoryRepository
) {

    @GetMapping("/data-updated-at")
    fun dataUpdatedAt(): Map<String, OffsetDateTime?> = mapOf(
        "updatedAt" to historyRepository.findLatestUpdatedAt()?.atZone(KST)?.toOffsetDateTime()
    )

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
