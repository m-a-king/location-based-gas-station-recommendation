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

    /** polyline 직선 거리 ≤ 2km 필터로 dead-zone 후보 제거 */
    TIGHT_CORRIDOR,

    /** 가격 오름차순 상위 30개 하드캡 — Kakao 호출 수 절대 상한 보장 */
    PRICE_CAPPED
}
