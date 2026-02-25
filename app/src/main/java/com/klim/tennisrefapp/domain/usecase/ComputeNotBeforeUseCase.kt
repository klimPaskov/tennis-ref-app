package com.klim.tennisrefapp.domain.usecase

import com.klim.tennisrefapp.domain.model.NotBeforeInput
import kotlin.math.max

class ComputeNotBeforeUseCase {
    operator fun invoke(input: NotBeforeInput): Long? {
        var candidate = input.manualNotBeforeMillis
        for (availability in input.participants) {
            val endedAt = availability.lastEndedAtMillis ?: continue
            val restBased = endedAt + availability.restMinutes * MINUTE_IN_MILLIS
            candidate = if (candidate == null) {
                restBased
            } else {
                max(candidate, restBased)
            }
        }
        return candidate
    }

    private companion object {
        const val MINUTE_IN_MILLIS = 60_000L
    }
}
