package com.klim.tennisrefapp.domain

import com.google.common.truth.Truth.assertThat
import com.klim.tennisrefapp.domain.model.MatchDependency
import com.klim.tennisrefapp.domain.model.SlotReference
import com.klim.tennisrefapp.domain.usecase.ResolveSlotUseCase
import org.junit.Test

class ResolveSlotUseCaseTest {
    private val useCase = ResolveSlotUseCase()

    @Test
    fun directTeamIsAlwaysResolved() {
        val result = useCase(SlotReference.DirectTeam(teamId = 7L), emptyMap())

        assertThat(result.isResolved).isTrue()
        assertThat(result.resolvedTeamId).isEqualTo(7L)
        assertThat(result.isBye).isFalse()
    }

    @Test
    fun winnerSlotResolvesWhenDependencyHasWinner() {
        val deps = mapOf(10L to MatchDependency(matchId = 10L, winnerTeamId = 22L))

        val result = useCase(SlotReference.WinnerOfMatch(matchId = 10L), deps)

        assertThat(result.isResolved).isTrue()
        assertThat(result.resolvedTeamId).isEqualTo(22L)
        assertThat(result.dependencyMatchId).isEqualTo(10L)
    }

    @Test
    fun winnerSlotIsUnresolvedWhenDependencyWinnerMissing() {
        val deps = mapOf(10L to MatchDependency(matchId = 10L, winnerTeamId = null))

        val result = useCase(SlotReference.WinnerOfMatch(matchId = 10L), deps)

        assertThat(result.isResolved).isFalse()
        assertThat(result.resolvedTeamId).isNull()
    }

    @Test
    fun byeSlotIsResolvedAsBye() {
        val result = useCase(SlotReference.Bye, emptyMap())

        assertThat(result.isResolved).isTrue()
        assertThat(result.isBye).isTrue()
        assertThat(result.resolvedTeamId).isNull()
    }
}
