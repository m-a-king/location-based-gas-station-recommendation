package com.kumoh.lbs.util

import com.kumoh.lbs.domain.LatLon

interface CoordinateConverter {
    fun katecToWgs84(katecX: Double, katecY: Double): LatLon
}
