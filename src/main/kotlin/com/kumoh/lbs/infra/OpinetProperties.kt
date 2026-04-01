package com.kumoh.lbs.infra

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "opinet")
data class OpinetProperties(
    val apiKey: String,
    val userId: String,
    val password: String
)
