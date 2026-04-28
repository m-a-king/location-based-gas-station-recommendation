package com.kumoh.lbs.gasstation.domain

/**
 * 경로 기반 추천의 후보 수집 cascade 경계값.
 *
 * 상수 이름은 설계 문서(docs/route-recommendation-design.md)의 용어를 그대로 따른다.
 */
object CandidateCascadePolicy {

    /**
     * PRICE_CAPPED 단계에서 남길 최대 후보 수.
     * Kakao 경유 경로 호출 수의 절대 상한(pruning이 이보다 더 줄일 수 있음).
     */
    const val HARD_CAP = 30

    /**
     * "경로상 후보"의 정의 — 폴리라인까지의 직선 거리(m) 임계값.
     * 이 거리 이내 후보의 최저가가 ROUTE_PRICE_CEILING 단계의 cap이 된다.
     * 식 (2) 하한 도출 시 우회 비용 ≈ 0 가정이 성립하도록 충분히 작게 설정.
     */
    const val ON_ROUTE_RADIUS_METERS = 500.0
}
