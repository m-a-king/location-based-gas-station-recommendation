package com.kumoh.lbs.domain

import com.kumoh.lbs.util.CoordinateConverter

class Coordinate private constructor(
    val katec: Katec,
    val wgs84: Wgs84
) {
    /** KATEC 좌표: x = Easting(경도 방향), y = Northing(위도 방향) */
    data class Katec(val x: Double, val y: Double)

    /** WGS84 좌표: latitude = 위도(남북), longitude = 경도(동서) */
    data class Wgs84(val latitude: Double, val longitude: Double)

    companion object {
        fun fromKatec(katec: Katec): Coordinate {
            val wgs84 = CoordinateConverter.katecToWgs84(katec)
            return Coordinate(katec, wgs84)
        }

        fun fromWgs84(wgs84: Wgs84): Coordinate {
            val katec = CoordinateConverter.wgs84ToKatec(wgs84)
            return Coordinate(katec, wgs84)
        }
    }
}
