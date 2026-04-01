package com.kumoh.lbs.batch.client

import com.kumoh.lbs.common.config.OpinetProperties
import java.nio.charset.Charset

/**
 * 로컬 전용 테스트 — 자격증명 하드코딩, git 제외 (.gitignore 등록됨)
 * Run > main() 으로 직접 실행
 */
private const val USER_ID  = "which229"
private const val PASSWORD = "which229!"
private const val API_KEY  = "F260316467"

fun main() {
    val downloader = OpinetCsvDownloader(OpinetProperties(API_KEY, USER_ID, PASSWORD))

    println("=== OPINET CSV 다운로드 테스트 ===")

    val bytes = try {
        downloader.downloadCurrentPriceCsv()
    } catch (e: Exception) {
        println("실패: ${e.message}")
        e.printStackTrace()
        return
    }

    val text  = bytes.toString(Charset.forName("MS949"))
    val lines = text.lines().filter { it.isNotBlank() }

    println("성공: ${bytes.size} bytes, ${lines.size}행")
    println("\n--- 상위 5행 ---")
    lines.take(5).forEachIndexed { i, line -> println("[$i] $line") }
}
