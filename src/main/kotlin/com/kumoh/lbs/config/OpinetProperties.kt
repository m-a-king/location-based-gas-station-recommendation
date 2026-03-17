package com.kumoh.lbs.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "opinet")
data class OpinetProperties(
    val apiKey: String
)
