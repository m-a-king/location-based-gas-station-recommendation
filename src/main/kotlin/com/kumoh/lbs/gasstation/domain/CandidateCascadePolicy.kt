package com.kumoh.lbs.gasstation.domain

/**
 * 경로 기반 추천의 후보 수집 cascade 경계값.
 *
 * 상수 이름은 설계 문서(docs/route-recommendation-design.md)의 용어를 그대로 따른다.
 * 이름만 보면 역할이 즉시 와닿지 않지만, 설계 문서·설계 토의와 어휘를 일치시키기 위해 유지한다.
 */
object CandidateCascadePolicy {

    /**
     * POLYLINE_MBR 단계 후보 수가 이 값을 초과하면 TIGHT_CORRIDOR 단계로 좁힌다.
     * cascade 진입 트리거.
     */
    const val N_THRESHOLD = 30

    /**
     * PRICE_CAPPED 단계에서 남길 최대 후보 수.
     * Kakao 경유 경로 호출 수의 절대 상한(pruning이 이보다 더 줄일 수 있음).
     */
    const val HARD_CAP = 30

    /**
     * TIGHT_CORRIDOR 필터에서 polyline까지 허용되는 최대 거리(m).
     * 개인 운전자 수용 우회 범위(Kelley & Kuby 2013)를 근사.
     */
    const val TIGHT_CORRIDOR_METERS = 2_000.0
}
