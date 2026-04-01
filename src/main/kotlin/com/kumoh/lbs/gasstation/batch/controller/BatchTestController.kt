package com.kumoh.lbs.gasstation.batch.controller

import com.kumoh.lbs.gasstation.batch.client.OpinetCsvDownloader
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/batch")
class BatchTestController(
    private val opinetCsvDownloader: OpinetCsvDownloader
) {

    @GetMapping("/test-download")
    fun testDownload(): List<String> {
        val bytes = opinetCsvDownloader.downloadCurrentPriceCsv()
        return bytes.toString(OpinetCsvDownloader.CSV_CHARSET).lines().take(5)
    }
}
