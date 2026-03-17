package com.kumoh.lbs.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

@Entity
@Immutable
@Table(name = "moct_link")
class MoctLink(
    @Id
    @Column(name = "link_id")
    val linkId: String,

    @Column(name = "f_node")
    val fNode: String,

    @Column(name = "t_node")
    val tNode: String,

    @Column(name = "road_name")
    val roadName: String?,

    @Column(name = "road_rank")
    val roadRank: String?,

    @Column(name = "road_no")
    val roadNo: String?,

    @Column(name = "lanes")
    val lanes: Int?,

    @Column(name = "max_spd")
    val maxSpd: Int?,

    @Column(name = "length")
    val length: Double?,

    @Column(name = "f_longitude")
    val fLongitude: Double,

    @Column(name = "f_latitude")
    val fLatitude: Double,

    @Column(name = "t_longitude")
    val tLongitude: Double,

    @Column(name = "t_latitude")
    val tLatitude: Double
)
