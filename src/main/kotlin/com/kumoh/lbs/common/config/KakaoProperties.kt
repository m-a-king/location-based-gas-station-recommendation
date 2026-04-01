package com.kumoh.lbs.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kakao")
data class KakaoProperties(
    val apiKey: String
)
