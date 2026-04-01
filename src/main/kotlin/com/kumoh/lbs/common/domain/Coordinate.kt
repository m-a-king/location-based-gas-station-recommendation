package com.kumoh.lbs.common.domain

import com.kumoh.lbs.common.util.CoordinateConverter

class Coordinate private constructor(
    katecProvider: () -> Katec,
    wgs84Provider: () -> Wgs84
) {
    val katec: Katec by lazy(katecProvider)
    val wgs84: Wgs84 by lazy(wgs84Provider)

    /** KATEC 좌표: x = Easting(경도 방향), y = Northing(위도 방향) */
    data class Katec(val x: Double, val y: Double)

    /** WGS84 좌표: latitude = 위도(남북), longitude = 경도(동서) */
    data class Wgs84(val latitude: Double, val longitude: Double)

    companion object {
        fun fromKatec(katec: Katec) = Coordinate(
            katecProvider = { katec },
            wgs84Provider = { CoordinateConverter.katecToWgs84(katec) }
        )

        fun fromWgs84(wgs84: Wgs84) = Coordinate(
            katecProvider = { CoordinateConverter.wgs84ToKatec(wgs84) },
            wgs84Provider = { wgs84 }
        )
    }
}
