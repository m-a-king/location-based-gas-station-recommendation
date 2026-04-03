package com.kumoh.lbs.gasstation.repository

import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.domain.GasStationPrice
import com.kumoh.lbs.gasstation.domain.GasStationPriceId
import org.springframework.data.jpa.repository.JpaRepository

interface GasStationPriceRepository : JpaRepository<GasStationPrice, GasStationPriceId> {
    fun findAllByIdStationIdInAndIdFuelType(stationIds: List<String>, fuelType: FuelType): List<GasStationPrice>
}
