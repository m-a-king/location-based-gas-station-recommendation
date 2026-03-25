package com.kumoh.lbs.config

import com.kumoh.lbs.client.ItsClient
import com.kumoh.lbs.client.KakaoDirectionsClient
import com.kumoh.lbs.client.OpinetClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {

    @Bean
    fun opinetRestClient(): RestClient {
        val converter = JacksonJsonHttpMessageConverter()
        converter.supportedMediaTypes = listOf(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)

        return RestClient.builder()
            .baseUrl(OpinetClient.BASE_URL)
            .configureMessageConverters { it.addCustomConverter(converter) }
            .build()
    }

    @Bean
    fun itsRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(ItsClient.BASE_URL)
            .build()

    @Bean
    fun kakaoRestClient(): RestClient =
        RestClient.builder()
            .baseUrl(KakaoDirectionsClient.BASE_URL)
            .build()
}
