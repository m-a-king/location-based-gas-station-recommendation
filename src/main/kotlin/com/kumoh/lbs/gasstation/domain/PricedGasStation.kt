package com.kumoh.lbs.gasstation.domain

/**
 * 특정 유종의 가격이 결합된 주유소.
 *
 * `GasStation`(엔티티)은 가격을 직접 갖지 않고 `GasStationPrice` 엔티티로 별도 관리된다.
 * 서비스 파이프라인에서 "이 주유소는 이 유종 가격이 있다"가 보장된 시점부터의 VO로,
 * 반경/경로 추천 양쪽에서 공통 어휘로 쓰인다.
 */
data class PricedGasStation(
    val station: GasStation,
    val price: Int
)
