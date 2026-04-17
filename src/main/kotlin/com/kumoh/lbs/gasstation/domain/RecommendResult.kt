package com.kumoh.lbs.gasstation.domain

data class RecommendResult(
    val scored: List<ScoredGasStation>,
    /**
     * 절감액 계산 기준가. `scored`는 limit개만 담으므로 자기 자신의 maxOf로는
     * "후보군 대비 얼마나 싸게 샀는가"를 계산할 수 없어, 서비스 단에서 가격 결합 직후
     * (pruning 전) 최고가를 포획해 전파한다.
     */
    val savingsBaselinePrice: Int
) {
    companion object {
        fun empty() = RecommendResult(scored = emptyList(), savingsBaselinePrice = 0)
    }
}
