package com.kumoh.lbs.infra

import com.kumoh.lbs.gasstation.batch.client.KakaoLocalClient
import com.kumoh.lbs.gasstation.client.KakaoDirectionsClient
import com.kumoh.lbs.gasstation.client.OpinetClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig {

    companion object {
        private val CONNECT_TIMEOUT = Duration.ofSeconds(3)
        private val READ_TIMEOUT = Duration.ofSeconds(5)
    }

    @Bean
    fun opinetRestClient(): RestClient {
        val converter = JacksonJsonHttpMessageConverter()
        converter.supportedMediaTypes = listOf(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)

        return RestClient.builder()
            .baseUrl(OpinetClient.BASE_URL)
            .requestFactory(createRequestFactory())
            .configureMessageConverters { it.addCustomConverter(converter) }
            .build()
    }

    @Bean
    fun kakaoRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(KakaoDirectionsClient.BASE_URL)
            .requestFactory(createRequestFactory())
            .build()

    @Bean
    fun kakaoLocalRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(KakaoLocalClient.BASE_URL)
            .requestFactory(createRequestFactory())
            .build()

    private fun createRequestFactory() = SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(CONNECT_TIMEOUT)
        setReadTimeout(READ_TIMEOUT)
    }
}
