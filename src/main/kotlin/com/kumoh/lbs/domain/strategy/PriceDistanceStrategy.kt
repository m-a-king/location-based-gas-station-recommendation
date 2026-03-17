package com.kumoh.lbs.domain.strategy

import com.kumoh.lbs.domain.GasStation
import org.springframework.stereotype.Component

@Component
class PriceDistanceStrategy : ScoringStrategy {

    override fun score(station: GasStation, trafficSpeed: Double?): Double {
        val priceScore = station.price.toDouble()
        val distanceScore = station.distance * DISTANCE_WEIGHT
        val trafficPenalty = calculateTrafficPenalty(trafficSpeed)
        return priceScore + distanceScore + trafficPenalty
    }

    /**
     * 교통 혼잡도 페널티 계산.
     * 속도가 느릴수록(혼잡할수록) 페널티가 커집니다.
     * - trafficSpeed == null: 교통 데이터 없음 → 페널티 0
     * - 속도 0~20 km/h: 심한 정체 → 높은 페널티
     * - 속도 20~40 km/h: 서행 → 중간 페널티
     * - 속도 40+ km/h: 원활 → 낮은 페널티
     */
    private fun calculateTrafficPenalty(speed: Double?): Double {
        if (speed == null) return 0.0
        val normalizedSpeed = speed.coerceIn(1.0, 100.0)
        return (100.0 - normalizedSpeed) * TRAFFIC_WEIGHT
    }

    companion object {
        private const val DISTANCE_WEIGHT = 0.5
        private const val TRAFFIC_WEIGHT = 2.0
    }
}
