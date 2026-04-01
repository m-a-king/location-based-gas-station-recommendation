package com.kumoh.lbs.gasstation.domain

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDate

@Entity
@Table(name = "gas_station_price")
class GasStationPrice(
    @EmbeddedId
    val id: GasStationPriceId,

    @MapsId("stationId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    val station: GasStation,

    @Column(nullable = false)
    var price: Int,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDate
)

@Embeddable
data class GasStationPriceId(
    @Column(name = "station_id")
    val stationId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type")
    val fuelType: FuelType
) : Serializable
