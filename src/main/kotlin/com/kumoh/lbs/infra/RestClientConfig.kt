package com.kumoh.lbs.infra

import com.kumoh.lbs.gasstation.batch.client.KakaoLocalClient
import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.client.OpinetClient
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 외부 HTTP 호출용 클라이언트 및 병렬 dispatch executor 구성.
 *
 * 커넥션 풀(Apache HttpClient 5 PoolingHttpClientConnectionManager):
 * - MAX_TOTAL: 앱 전체 동시 커넥션 상한
 * - MAX_PER_ROUTE: 단일 호스트(Kakao 등)당 동시 커넥션 상한. 파동 병렬 waveSize보다 여유 있게.
 * - VALIDATE_AFTER_INACTIVITY: 5초 이상 놀던 커넥션은 재사용 전 생존 체크 (서버측 timeout 대응)
 * - TTL: 커넥션 최대 수명. 서버의 keep-alive timeout(보통 60s)보다 짧아야 좀비 재사용을 피함.
 */
@Configuration
class RestClientConfig {

    companion object {
        private val CONNECT_TIMEOUT = Timeout.ofSeconds(3)
        private val RESPONSE_TIMEOUT = Timeout.ofSeconds(5)
        private val SOCKET_TIMEOUT = Timeout.ofSeconds(5)

        private const val MAX_TOTAL = 100
        private const val MAX_PER_ROUTE = 20
        private val VALIDATE_AFTER_INACTIVITY = TimeValue.ofSeconds(5)
        private val CONNECTION_TTL = TimeValue.ofSeconds(50)
        private val IDLE_EVICTION = TimeValue.ofSeconds(30)
    }

    @Bean(destroyMethod = "close")
    fun httpConnectionManager(): PoolingHttpClientConnectionManager {
        val manager = PoolingHttpClientConnectionManager()
        manager.maxTotal = MAX_TOTAL
        manager.defaultMaxPerRoute = MAX_PER_ROUTE
        manager.setDefaultConnectionConfig(
            ConnectionConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .setTimeToLive(CONNECTION_TTL)
                .setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY)
                .build()
        )
        return manager
    }

    @Bean(destroyMethod = "close")
    fun pooledHttpClient(connectionManager: PoolingHttpClientConnectionManager): CloseableHttpClient {
        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(2))
            .setResponseTimeout(RESPONSE_TIMEOUT)
            .build()
        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(IDLE_EVICTION)
            .evictExpiredConnections()
            .build()
    }

    @Bean
    fun opinetRestClient(httpClient: CloseableHttpClient): RestClient {
        val converter = JacksonJsonHttpMessageConverter()
        converter.supportedMediaTypes = listOf(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)

        return RestClient.builder()
            .baseUrl(OpinetClient.BASE_URL)
            .requestFactory(HttpComponentsClientHttpRequestFactory(httpClient))
            .configureMessageConverters { it.addCustomConverter(converter) }
            .build()
    }

    @Bean
    fun kakaoRestClient(httpClient: CloseableHttpClient): RestClient =
        RestClient.builder()
            .baseUrl(KakaoDirectionsClient.BASE_URL)
            .requestFactory(HttpComponentsClientHttpRequestFactory(httpClient))
            .build()

    @Bean
    fun kakaoLocalRestClient(httpClient: CloseableHttpClient): RestClient =
        RestClient.builder()
            .baseUrl(KakaoLocalClient.BASE_URL)
            .requestFactory(HttpComponentsClientHttpRequestFactory(httpClient))
            .build()

    @Bean(destroyMethod = "shutdown")
    fun routeExecutor(properties: RouteRecommenderProperties): ExecutorService {
        val counter = AtomicInteger()
        return Executors.newFixedThreadPool(properties.maxParallelism) { runnable ->
            Thread(runnable, "route-kakao-${counter.incrementAndGet()}").apply { isDaemon = true }
        }
    }
}
