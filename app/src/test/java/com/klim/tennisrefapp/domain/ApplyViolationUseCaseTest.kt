package com.klim.tennisrefapp.domain

import com.google.common.truth.Truth.assertThat
import com.klim.tennisrefapp.domain.model.ViolationCategory
import com.klim.tennisrefapp.domain.model.ViolationCounts
import com.klim.tennisrefapp.domain.usecase.ApplyViolationUseCase
import org.junit.Test

class ApplyViolationUseCaseTest {
    private val useCase = ApplyViolationUseCase()

    @Test
    fun codeViolationAdvancesToPointPenaltyOnSecondOffence() {
        val current = ViolationCounts(codeViolationCount = 1)

        val decision = useCase.apply(current, ViolationCategory.CODE)

        assertThat(decision.updatedCounts.codeViolationCount).isEqualTo(2)
        assertThat(decision.guidance.appliedPenalty).isEqualTo("Point penalty")
        assertThat(decision.guidance.nextPenalty).isEqualTo("Game penalty")
    }

    @Test
    fun codeViolationUsesSupervisorDecisionAfterThird() {
        val current = ViolationCounts(codeViolationCount = 3)

        val decision = useCase.apply(current, ViolationCategory.CODE)

        assertThat(decision.updatedCounts.codeViolationCount).isEqualTo(4)
        assertThat(decision.guidance.appliedPenalty).isEqualTo("Supervisor decision")
        assertThat(decision.guidance.nextPenalty).isEqualTo("Supervisor decision")
    }

    @Test
    fun timeViolationServerBecomesFaultAfterFirst() {
        val current = ViolationCounts(timeViolationCountServer = 1)

        val decision = useCase.apply(current, ViolationCategory.TIME_SERVER)

        assertThat(decision.updatedCounts.timeViolationCountServer).isEqualTo(2)
        assertThat(decision.guidance.appliedPenalty).isEqualTo("Fault")
        assertThat(decision.guidance.nextPenalty).isEqualTo("Fault")
    }

    @Test
    fun timeViolationReceiverBecomesPointPenaltyAfterFirst() {
        val current = ViolationCounts(timeViolationCountReceiver = 1)

        val decision = useCase.apply(current, ViolationCategory.TIME_RECEIVER)

        assertThat(decision.updatedCounts.timeViolationCountReceiver).isEqualTo(2)
        assertThat(decision.guidance.appliedPenalty).isEqualTo("Point penalty")
        assertThat(decision.guidance.nextPenalty).isEqualTo("Point penalty")
    }
}
