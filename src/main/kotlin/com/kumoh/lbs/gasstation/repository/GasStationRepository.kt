package com.kumoh.lbs.gasstation.repository

import com.kumoh.lbs.gasstation.domain.GasStation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface GasStationRepository : JpaRepository<GasStation, String> {
    @Query("SELECT g.id as id, g.latitude as latitude, g.longitude as longitude FROM GasStation g")
    fun findAllCoord(): List<StationCoord>
}

interface StationCoord {
    val id: String
    val latitude: Double
    val longitude: Double
}
