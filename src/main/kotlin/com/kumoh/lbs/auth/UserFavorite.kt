package com.kumoh.lbs.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

/**
 * 사용자가 즐겨찾기한 주유소. 사용자(users.id)와 주유소(gas_station.id)를 잇는 중간 엔티티다.
 * 주유소는 OPINET 배치로 정기 교체되므로 gas_station_id에는 FK를 걸지 않고 단순 참조로 둔다.
 * (user, station) 조합은 DB UNIQUE 제약으로 중복 즐겨찾기를 막는다.
 */
@Entity
@Table(name = "user_favorites")
class UserFavorite(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "gas_station_id", nullable = false)
    val gasStationId: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null
)
