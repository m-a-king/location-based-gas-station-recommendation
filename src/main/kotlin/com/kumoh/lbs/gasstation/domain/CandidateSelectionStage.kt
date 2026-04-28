package com.kumoh.lbs.gasstation.domain

/**
 * 경로 기반 추천의 후보 수집 cascade 단계.
 *
 * 1단계에서 관대하게 수집한 뒤, 후보가 N_THRESHOLD를 초과하면
 * 2→3단계로 점진 압축한다. 최종 확정 단계가 로그에 기록된다.
 */
enum class CandidateSelectionStage {
    /** polyline MBR + 동적 buffer 내 전체 주유소 수집 */
    POLYLINE_MBR,

    /**
     * 경로상 후보(폴리라인까지 직선 ≤ ON_ROUTE_RADIUS_METERS)의 최저가를
     * 기준으로 가격 ≤ p_route 후보만 보존.
     * 식 (2) 하한에 의해 가격이 더 비싼 후보는 1위가 될 수 없으므로
     * 외부 호출 전에 정확히 배제 가능.
     */
    ROUTE_PRICE_CEILING,

    /** 가격 오름차순 상위 30개 하드캡 — Kakao 호출 수 절대 상한 보장 */
    PRICE_CAPPED
}
