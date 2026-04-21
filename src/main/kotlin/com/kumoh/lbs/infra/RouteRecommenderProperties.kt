package com.kumoh.lbs.infra

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 경로 기반 추천의 Kakao Directions 호출 병렬도 설정.
 *
 * - waveSize: 첫 파동(= limit) 이후 파동의 병렬 호출 수. 클수록 wall time↓, 낭비 호출 상한↑.
 * - maxParallelism: routeExecutor 스레드 풀 상한. waveSize 이상이어야 파동 병렬도가 발휘된다.
 */
@ConfigurationProperties(prefix = "recommender.route")
data class RouteRecommenderProperties(
    val waveSize: Int = 3,
    val maxParallelism: Int = 10
) {
    init {
        require(waveSize >= 1) { "waveSize는 1 이상이어야 합니다: $waveSize" }
        require(maxParallelism >= waveSize) {
            "maxParallelism($maxParallelism)은 waveSize($waveSize) 이상이어야 합니다."
        }
    }
}
