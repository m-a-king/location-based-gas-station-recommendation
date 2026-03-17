package com.kumoh.lbs.util

import com.kumoh.lbs.domain.LatLon
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import org.springframework.stereotype.Component

@Component
class Proj4CoordinateConverter : CoordinateConverter {

    private val transform = run {
        val crsFactory = CRSFactory()
        val katec = crsFactory.createFromParameters(
            "KATEC",
            "+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000 +ellps=GRS80 +units=m +no_defs"
        )
        val wgs84 = crsFactory.createFromName("EPSG:4326")
        CoordinateTransformFactory().createTransform(katec, wgs84)
    }

    override fun katecToWgs84(katecX: Double, katecY: Double): LatLon {
        val src = ProjCoordinate(katecX, katecY)
        val dst = ProjCoordinate()
        transform.transform(src, dst)
        return LatLon(latitude = dst.y, longitude = dst.x)
    }
}
