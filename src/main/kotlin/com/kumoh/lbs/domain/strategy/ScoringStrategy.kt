package com.kumoh.lbs.domain.strategy

import com.kumoh.lbs.domain.GasStation

interface ScoringStrategy {

    fun score(station: GasStation, trafficSpeed: Double? = null): Double
}
