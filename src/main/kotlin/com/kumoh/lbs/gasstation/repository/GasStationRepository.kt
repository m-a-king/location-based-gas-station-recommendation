package com.kumoh.lbs.gasstation.repository

import com.kumoh.lbs.gasstation.domain.GasStation
import com.kumoh.lbs.geo.BoundingBox
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface GasStationRepository : JpaRepository<GasStation, String> {

    @Query("SELECT g.id as id, g.latitude as latitude, g.longitude as longitude FROM GasStation g")
    fun findAllCoord(): List<StationCoord>

    @Query("""
        SELECT g FROM GasStation g
        WHERE g.latitude  BETWEEN :#{#bounds.minLat} AND :#{#bounds.maxLat}
        AND   g.longitude BETWEEN :#{#bounds.minLon} AND :#{#bounds.maxLon}
    """)
    fun findInBounds(bounds: BoundingBox): List<GasStation>
}

interface StationCoord {
    val id: String
    val latitude: Double
    val longitude: Double
}
