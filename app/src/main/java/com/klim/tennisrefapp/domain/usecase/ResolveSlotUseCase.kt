package com.klim.tennisrefapp.domain.usecase

import com.klim.tennisrefapp.domain.model.MatchDependency
import com.klim.tennisrefapp.domain.model.SlotReference
import com.klim.tennisrefapp.domain.model.SlotResolution

class ResolveSlotUseCase {
    operator fun invoke(
        slotReference: SlotReference,
        dependencies: Map<Long, MatchDependency>,
    ): SlotResolution {
        return when (slotReference) {
            is SlotReference.DirectTeam -> SlotResolution(
                resolvedTeamId = slotReference.teamId,
                isBye = false,
                dependencyMatchId = null,
            )

            is SlotReference.WinnerOfMatch -> {
                val dependency = dependencies[slotReference.matchId]
                SlotResolution(
                    resolvedTeamId = dependency?.winnerTeamId,
                    isBye = false,
                    dependencyMatchId = slotReference.matchId,
                )
            }

            SlotReference.Bye -> SlotResolution(
                resolvedTeamId = null,
                isBye = true,
                dependencyMatchId = null,
            )
        }
    }
}
