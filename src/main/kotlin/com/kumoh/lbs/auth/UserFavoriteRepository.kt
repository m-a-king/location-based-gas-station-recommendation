package com.kumoh.lbs.auth

import org.springframework.data.jpa.repository.JpaRepository

interface UserFavoriteRepository : JpaRepository<UserFavorite, Long> {

    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<UserFavorite>

    fun existsByUserIdAndGasStationId(userId: Long, gasStationId: String): Boolean

    fun deleteByUserIdAndGasStationId(userId: Long, gasStationId: String)
}
