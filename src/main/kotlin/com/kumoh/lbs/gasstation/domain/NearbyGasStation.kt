package com.kumoh.lbs.gasstation.domain

/**
 * 반경 탐색 결과: 가격 결합된 주유소 + OPINET이 돌려준 직선거리(m).
 *
 * 경로 추천 파이프라인의 `PricedGasStation`과 공통 기반을 공유하되,
 * 반경 경로에서만 의미 있는 `distanceMeters`를 덧붙인다.
 */
data class NearbyGasStation(
    val priced: PricedGasStation,
    val distanceMeters: Double
) {
    val station: GasStation get() = priced.station
    val price: Int get() = priced.price
}
