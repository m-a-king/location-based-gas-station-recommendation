package com.kumoh.lbs.gasstation.domain

import com.kumoh.lbs.geo.Coordinate
import jakarta.persistence.*

@Entity
@Table(name = "gas_station")
class GasStation(
    @Id
    val id: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val brand: String,

    val address: String? = null,

    @Column(name = "is_self", nullable = false)
    val isSelf: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: StationType = StationType.GAS_STATION,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double
) {
    val coordinate: Coordinate
        get() = Coordinate.fromWgs84(Coordinate.Wgs84(latitude, longitude))
}
