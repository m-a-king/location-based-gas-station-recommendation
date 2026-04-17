package com.kumoh.lbs.gasstation.domain

data class RecommendResult(
    val scored: List<ScoredGasStation>,
    val maxPriceInCandidates: Int
) {
    companion object {
        fun empty() = RecommendResult(scored = emptyList(), maxPriceInCandidates = 0)
    }
}
