package com.klim.tennisrefapp.domain.model

enum class ViolationCategory {
    CODE,
    TIME_SERVER,
    TIME_RECEIVER,
}

data class ViolationCounts(
    val codeViolationCount: Int = 0,
    val timeViolationCountServer: Int = 0,
    val timeViolationCountReceiver: Int = 0,
)

data class ViolationGuidance(
    val offenceNumber: Int,
    val appliedPenalty: String,
    val nextPenalty: String,
)

data class ViolationDecision(
    val updatedCounts: ViolationCounts,
    val guidance: ViolationGuidance,
)
