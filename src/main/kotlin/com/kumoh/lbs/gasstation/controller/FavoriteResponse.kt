package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.auth.UserFavorite
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStation
import java.time.LocalDateTime

/**
 * 즐겨찾기 주유소 응답. 주유소 기본 정보에 더해, 사용자 유종 기준 현재 가격(price)을 함께 내려준다.
 * 가격 데이터가 없으면 price는 null이며, 어느 유종 기준 가격인지 fuelType으로 함께 표기한다.
 */
data class FavoriteResponse(
    val opinetStationId: String,
    val name: String,
    val brand: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val fuelType: FuelType,
    val price: Int?,
    val favoritedAt: LocalDateTime?
) {
    companion object {
        fun from(favorite: UserFavorite, station: GasStation, fuelType: FuelType, price: Int?) =
            FavoriteResponse(
                opinetStationId = station.id,
                name = station.name,
                brand = station.brand,
                address = station.address,
                latitude = station.latitude,
                longitude = station.longitude,
                fuelType = fuelType,
                price = price,
                favoritedAt = favorite.createdAt
            )
    }
}
