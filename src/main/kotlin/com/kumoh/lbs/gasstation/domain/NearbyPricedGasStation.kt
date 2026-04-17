package com.kumoh.lbs.gasstation.domain

/**
 * 반경 탐색 결과: 가격 결합된 주유소 + OPINET이 돌려준 직선거리(m).
 *
 * "Nearby"는 거리 의미만 내포하므로 가격 결합 보장을 이름에 명시(`Priced`)해
 * 경로 추천의 `PricedGasStation`과 타입 계보를 드러낸다.
 */
data class NearbyPricedGasStation(
    val priced: PricedGasStation,
    val distanceMeters: Double
) {
    val station: GasStation get() = priced.station
    val price: Int get() = priced.price
}
