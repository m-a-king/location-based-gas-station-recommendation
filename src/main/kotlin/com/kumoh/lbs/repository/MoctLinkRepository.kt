package com.kumoh.lbs.repository

import com.kumoh.lbs.domain.entity.MoctLink
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MoctLinkRepository : JpaRepository<MoctLink, String> {

    @Query(
        value = """
            SELECT * FROM moct_link
            WHERE (f_longitude BETWEEN :minLon AND :maxLon AND f_latitude BETWEEN :minLat AND :maxLat)
               OR (t_longitude BETWEEN :minLon AND :maxLon AND t_latitude BETWEEN :minLat AND :maxLat)
        """,
        nativeQuery = true
    )
    fun findLinksInBoundingBox(
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double
    ): List<MoctLink>
}
