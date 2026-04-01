package com.kumoh.lbs.gasstation.batch.client

import com.kumoh.lbs.infra.OpinetProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher

private val logger = KotlinLogging.logger {}

/**
 * OPINET 현재 판매가격 CSV 다운로더.
 *
 * 흐름:
 *   1. GET  /                                    → 초기 JSESSIONID + WMONID 쿠키 획득
 *   2. GET  /rsaGen.do                           → RSA 공개키(module, exponent) 획득
 *   3. RSA PKCS1v15 암호화                       → 비밀번호를 hex 문자열로 변환
 *   4. POST /doLogin.do                          → 인증된 JSESSIONID 획득
 *   5. GET  /user/opdown/opDownload.do           → 다운로드 권한 세션 확립 (필수)
 *   6. curl nfl.opinet.co.kr/ts.wseq            → NetFunnel 키 획득
 *      (nfl.opinet.co.kr 는 TLS_RSA_* 전용으로 Java 25+ 에서 curl 로 우회)
 *   7. POST /user/main/main_download_excel.do   → CSV 바이트 반환 (인코딩: MS949, PAGE_DIV_5)
 */
@Component
class OpinetCsvDownloader(
    private val properties: OpinetProperties
) {

    companion object {
        val CSV_CHARSET: Charset = Charset.forName("MS949")

        private const val BASE_URL = "https://www.opinet.co.kr"
        private const val DOWNLOAD_URL = "$BASE_URL/user/main/main_download_excel.do"
        private const val DOWNLOAD_PAGE_URL = "$BASE_URL/user/opdown/opDownload.do"
        private const val NETFUNNEL_URL = "https://nfl.opinet.co.kr/ts.wseq"
        private const val LOGIN_VIEW_URL = "$BASE_URL/"
        private const val RSA_GEN_URL = "$BASE_URL/rsaGen.do"
        private const val DO_LOGIN_URL = "$BASE_URL/doLogin.do"

        private val NETFUNNEL_KEY_REGEX = Regex("key=([A-Fa-f0-9]+)")
        private val RSA_MODULE_REGEX = Regex("\"publicKeyModule\"\\s*:\\s*\"([^\"]+)\"")
        private val RSA_EXPONENT_REGEX = Regex("\"publicKeyExponent\"\\s*:\\s*\"([^\"]+)\"")
    }

    fun downloadCurrentPriceCsv(): ByteArray {
        val sessionId = login()
        visitDownloadPage(sessionId)
        val netFunnelKey = fetchNetFunnelKey()
        return postDownload(netFunnelKey, sessionId)
    }

    private fun visitDownloadPage(sessionId: String) {
        val conn = openGet(DOWNLOAD_PAGE_URL, referer = BASE_URL)
        conn.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
        conn.inputStream.readBytes()
        conn.disconnect()
    }

    private fun login(): String {
        val (wmonId, preSessionId) = getInitialSession()
        val (module, exponent) = fetchRsaKey(preSessionId, wmonId)
        val encryptedPwd = rsaEncrypt(properties.password, module, exponent)
        return doLogin(properties.userId, encryptedPwd, preSessionId, wmonId)
    }

    private fun getInitialSession(): Pair<String, String> {
        val conn = openGet(LOGIN_VIEW_URL, referer = BASE_URL)
        val cookies = conn.getHeaderFields()["Set-Cookie"] ?: emptyList()
        conn.inputStream.readBytes()
        conn.disconnect()

        val wmonId = cookies.find { it.startsWith("WMONID=") }
            ?.substringAfter("WMONID=")?.substringBefore(";") ?: ""
        val sessionId = cookies.find { it.startsWith("JSESSIONID=") }
            ?.substringAfter("JSESSIONID=")?.substringBefore(";")
            ?: error("초기 JSESSIONID 획득 실패")

        return wmonId to sessionId
    }

    private fun fetchRsaKey(sessionId: String, wmonId: String): Pair<String, String> {
        val conn = openGet(RSA_GEN_URL, referer = LOGIN_VIEW_URL)
        conn.setRequestProperty("Cookie", cookieHeader(sessionId, wmonId))

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        logger.debug { "RSA 키 응답: $response" }

        val module = RSA_MODULE_REGEX.find(response)?.groupValues?.get(1)
            ?: error("publicKeyModule 추출 실패. 응답: $response")
        val exponent = RSA_EXPONENT_REGEX.find(response)?.groupValues?.get(1)
            ?: error("publicKeyExponent 추출 실패. 응답: $response")

        return module to exponent
    }

    private fun rsaEncrypt(plaintext: String, module: String, exponent: String): String {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(module, 16), BigInteger(exponent, 16))
        )
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun doLogin(userId: String, encryptedPwd: String, sessionId: String, wmonId: String): String {
        val body = listOf("USR_ID" to userId, "PWD" to encryptedPwd, "REMEMBER_ME_YN" to "N")
            .joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

        val conn = URI(DO_LOGIN_URL).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Referer", LOGIN_VIEW_URL)
        conn.setRequestProperty("Origin", BASE_URL)
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.setRequestProperty("Cookie", cookieHeader(sessionId, wmonId))
        conn.outputStream.use { it.write(body.toByteArray()) }

        val newCookies = conn.getHeaderFields()["Set-Cookie"] ?: emptyList()
        val responseCode = conn.responseCode
        conn.inputStream.readBytes()
        conn.disconnect()

        val newSessionId = newCookies.find { it.startsWith("JSESSIONID=") }
            ?.substringAfter("JSESSIONID=")?.substringBefore(";")

        return when {
            newSessionId != null -> {
                logger.info { "OPINET 로그인 성공 (새 JSESSIONID 발급)" }
                newSessionId
            }
            responseCode in 200..302 -> {
                logger.info { "OPINET 로그인 성공 (HTTP $responseCode, 기존 세션 유지)" }
                sessionId
            }
            else -> error("OPINET 로그인 실패 (HTTP $responseCode)")
        }
    }

    private fun fetchNetFunnelKey(): String {
        val timestamp = System.currentTimeMillis()
        val url = "$NETFUNNEL_URL?opcode=5101&nfid=0" +
                "&prefix=NetFunnel.gRtype%3D5101%3B" +
                "&sid=service_1&aid=B7&js=yes&$timestamp"

        val response = fetchViaCurl(url)
        logger.debug { "NetFunnel 응답: $response" }

        return NETFUNNEL_KEY_REGEX.find(response)?.groupValues?.get(1)
            ?: error("NetFunnel 키 추출 실패. 응답: $response")
    }

    private fun fetchViaCurl(url: String): String {
        val proc = ProcessBuilder("curl", "-s", "--max-time", "30", "-A", "Mozilla/5.0", url)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        check(exit == 0) { "curl 실패 (exit=$exit): $output" }
        return output
    }

    private fun postDownload(netFunnelKey: String, sessionId: String): ByteArray {
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

        val body = listOf(
            "LPG_CD"        to "A",
            "DATE_DIV_CD"   to "",
            "PAGE_DIV"      to "PAGE_DIV_5",
            "SIDO_NM"       to "",
            "SIGUN_NM"      to "",
            "API_GBN"       to "A",
            "netfunnel_key" to netFunnelKey,
            "rdo1"          to "A",
            "rdo2"          to "A",
            "rdo3"          to "A",
            "rdo4"          to "X",
            "START_DT"      to today,
            "END_DT"        to today,
            "SIDO_CD"       to "",
            "SIGUN_CD"      to ""
        ).joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

        val conn = URI(DOWNLOAD_URL).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 10_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Referer", "$BASE_URL/user/opdown/opDownload.do")
        conn.setRequestProperty("Origin", BASE_URL)
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
        conn.outputStream.use { it.write(body.toByteArray()) }

        val bytes = conn.inputStream.readBytes()
        conn.disconnect()
        logger.info { "OPINET CSV 다운로드 완료: ${bytes.size} bytes" }
        return bytes
    }

    private fun openGet(url: String, referer: String): HttpURLConnection {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Referer", referer)
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        return conn
    }

    private fun cookieHeader(sessionId: String, wmonId: String) =
        if (wmonId.isNotEmpty()) "JSESSIONID=$sessionId; WMONID=$wmonId"
        else "JSESSIONID=$sessionId"

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
}
