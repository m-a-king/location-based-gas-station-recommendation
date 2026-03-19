package com.kumoh.lbs.repository

import com.kumoh.lbs.domain.BoundingBox
import com.kumoh.lbs.domain.entity.MoctLink
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MoctLinkRepository : JpaRepository<MoctLink, String> {

    fun findLinksIn(box: BoundingBox): List<MoctLink> =
        findLinksInBoundingBox(
            minLon = box.southWest.wgs84.longitude,
            maxLon = box.northEast.wgs84.longitude,
            minLat = box.southWest.wgs84.latitude,
            maxLat = box.northEast.wgs84.latitude
        )

    @Query(
        value = """
            SELECT * FROM moct_link
            WHERE LEAST(f_longitude, t_longitude) <= :maxLon
              AND GREATEST(f_longitude, t_longitude) >= :minLon
              AND LEAST(f_latitude, t_latitude) <= :maxLat
              AND GREATEST(f_latitude, t_latitude) >= :minLat
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
