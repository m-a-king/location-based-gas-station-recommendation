package com.kumoh.lbs.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "its")
data class ItsProperties(
    val apiKey: String
)
