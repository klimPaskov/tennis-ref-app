package com.klim.tennisrefapp.domain

import com.google.common.truth.Truth.assertThat
import com.klim.tennisrefapp.domain.model.NotBeforeInput
import com.klim.tennisrefapp.domain.model.PlayerAvailability
import com.klim.tennisrefapp.domain.usecase.ComputeNotBeforeUseCase
import org.junit.Test

class ComputeNotBeforeUseCaseTest {
    private val useCase = ComputeNotBeforeUseCase()

    @Test
    fun returnsManualNotBeforeWhenNoParticipantsHaveHistory() {
        val result = useCase(
            NotBeforeInput(
                manualNotBeforeMillis = 1_700_000_000_000,
                participants = listOf(
                    PlayerAvailability(playerId = 1, restMinutes = 30, lastEndedAtMillis = null),
                ),
            ),
        )

        assertThat(result).isEqualTo(1_700_000_000_000)
    }

    @Test
    fun returnsMaxOfManualAndRestConstraints() {
        val result = useCase(
            NotBeforeInput(
                manualNotBeforeMillis = 1_700_000_000_000,
                participants = listOf(
                    PlayerAvailability(playerId = 1, restMinutes = 20, lastEndedAtMillis = 1_700_000_300_000),
                    PlayerAvailability(playerId = 2, restMinutes = 45, lastEndedAtMillis = 1_700_000_100_000),
                ),
            ),
        )

        assertThat(result).isEqualTo(1_700_002_800_000)
    }

    @Test
    fun returnsNullWhenNoManualAndNoFinishedMatches() {
        val result = useCase(
            NotBeforeInput(
                manualNotBeforeMillis = null,
                participants = listOf(
                    PlayerAvailability(playerId = 1, restMinutes = 20, lastEndedAtMillis = null),
                ),
            ),
        )

        assertThat(result).isNull()
    }
}
