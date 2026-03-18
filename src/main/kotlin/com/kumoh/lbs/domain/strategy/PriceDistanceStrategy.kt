package com.kumoh.lbs.domain.strategy

import com.kumoh.lbs.domain.GasStation
import org.springframework.stereotype.Component

@Component
class PriceDistanceStrategy : ScoringStrategy {

    override fun score(station: GasStation): Double {
        val priceScore = station.price.toDouble()
        val distanceScore = station.distance * DISTANCE_WEIGHT
        return priceScore + distanceScore
    }

    companion object {
        private const val DISTANCE_WEIGHT = 0.5
    }
}
