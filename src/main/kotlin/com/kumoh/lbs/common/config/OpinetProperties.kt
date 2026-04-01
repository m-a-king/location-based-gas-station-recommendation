package com.kumoh.lbs.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "opinet")
data class OpinetProperties(
    val apiKey: String,
    val userId: String,
    val password: String
)
