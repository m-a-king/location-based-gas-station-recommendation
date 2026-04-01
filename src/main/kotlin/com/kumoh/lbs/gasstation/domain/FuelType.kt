package com.kumoh.lbs.gasstation.domain

enum class FuelType(val code: String, val description: String) {
    GASOLINE("B027", "휘발유"),
    DIESEL("D047", "경유"),
    PREMIUM_GASOLINE("B034", "고급휘발유"),
    LPG("K015", "LPG");

    companion object {
        fun fromCode(code: String): FuelType =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("지원하지 않는 유종 코드입니다: $code")
    }
}
