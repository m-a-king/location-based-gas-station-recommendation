package com.kumoh.lbs.infra

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kakao")
data class KakaoProperties(
    val apiKey: String
)
