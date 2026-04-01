package com.kumoh.lbs.controller

import com.kumoh.lbs.client.OpinetCsvDownloader
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.Charset

@RestController
@RequestMapping("/api/admin/batch")
class BatchTestController(
    private val opinetCsvDownloader: OpinetCsvDownloader
) {

    @GetMapping("/test-download")
    fun testDownload(): List<String> {
        val bytes = opinetCsvDownloader.downloadCurrentPriceCsv()
        return bytes.toString(Charset.forName("MS949")).lines().take(5)
    }
}
