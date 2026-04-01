package com.kumoh.lbs.infra

class ExternalApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
