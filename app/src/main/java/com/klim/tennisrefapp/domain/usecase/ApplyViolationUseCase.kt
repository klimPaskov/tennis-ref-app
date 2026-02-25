package com.klim.tennisrefapp.domain.usecase

import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.model.ViolationDecision
import com.klim.tennisrefapp.domain.model.ViolationGuidance

class ApplyViolationUseCase {
    fun apply(
        current: ViolationCounts,
        category: ViolationCategory,
    ): ViolationDecision {
        return when (category) {
            ViolationCategory.CODE -> {
                val offence = current.codeViolationCount + 1
                val updated = current.copy(codeViolationCount = offence)
                ViolationDecision(updated, codeGuidance(offence))
            }

            ViolationCategory.TIME_SERVER -> {
                val offence = current.timeViolationCountServer + 1
                val updated = current.copy(timeViolationCountServer = offence)
                ViolationDecision(updated, timeServerGuidance(offence))
            }

            ViolationCategory.TIME_RECEIVER -> {
                val offence = current.timeViolationCountReceiver + 1
                val updated = current.copy(timeViolationCountReceiver = offence)
                ViolationDecision(updated, timeReceiverGuidance(offence))
            }
        }
    }

    private fun codeGuidance(offence: Int): ViolationGuidance {
        val applied = when (offence) {
            1 -> "Warning"
            2 -> "Point penalty"
            3 -> "Game penalty"
            else -> "Supervisor decision"
        }
        val next = when (offence + 1) {
            1 -> "Warning"
            2 -> "Point penalty"
            3 -> "Game penalty"
            else -> "Supervisor decision"
        }
        return ViolationGuidance(offence, applied, next)
    }

    private fun timeServerGuidance(offence: Int): ViolationGuidance {
        val applied = if (offence == 1) "Warning" else "Fault"
        val next = if (offence + 1 == 1) "Warning" else "Fault"
        return ViolationGuidance(offence, applied, next)
    }

    private fun timeReceiverGuidance(offence: Int): ViolationGuidance {
        val applied = if (offence == 1) "Warning" else "Point penalty"
        val next = if (offence + 1 == 1) "Warning" else "Point penalty"
        return ViolationGuidance(offence, applied, next)
    }
}
