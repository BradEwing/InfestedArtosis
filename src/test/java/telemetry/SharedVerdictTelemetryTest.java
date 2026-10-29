package telemetry;

import org.junit.jupiter.api.Test;
import unit.squad.SquadStatus;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedVerdictTelemetryTest {

    @Test
    void theNeighbourCellsAreTheLastColumnsAndEmptyOnEveryOtherRow() {
        String[] columns = SquadDecisionLogger.HEADER.split(",", -1);
        SquadDecision context = new SquadDecision();

        assertEquals(Arrays.asList("partner_squad_id", "partner_distance", "opposed_reason", "shared_verdict_basis"),
                Arrays.asList(columns).subList(columns.length - 4, columns.length));
        assertEquals(Arrays.asList("NONE", "-1", "NONE", "NONE"), SquadDecisionLogger.neighbourCells(context));
    }

    @Test
    void theNeighbourCellsAreSetForOneRowAndClearedAfter() {
        SquadDecision context = new SquadDecision();
        context.setPartnerSquadId("abc");
        context.setPartnerDistance(300);
        context.setOpposedReason("BELOW_RETREAT");
        context.setSharedVerdictBasis("LOCK");

        assertEquals(Arrays.asList("abc", "300", "BELOW_RETREAT", "LOCK"), SquadDecisionLogger.neighbourCells(context));
        context.clearNeighbour();
        assertEquals(Arrays.asList("NONE", "-1", "NONE", "NONE"), SquadDecisionLogger.neighbourCells(context));
    }

    @Test
    void aNeighbourHoldIsKeyedOnTheHoldingNeighboursLockSoARenewedLockWritesANewRow() {
        int now = 1000;
        unit.squad.Squad held = new unit.squad.GroundSquad();
        held.setStatus(SquadStatus.FIGHT);
        unit.squad.Squad neighbour = new unit.squad.GroundSquad();
        neighbour.setStatus(SquadStatus.FIGHT);
        java.util.List<unit.squad.Squad> squads = Arrays.asList(held, neighbour);
        unit.squad.CombatSimulator.CombatResult retreat = unit.squad.CombatSimulator.CombatResult.RETREAT;

        assertEquals(SquadDecision.NOT_EVALUATED, SquadDecisionLogger.neighbourLockUntilFrame(held, squads, now));
        neighbour.startFightLock(now);
        int first = SquadDecisionLogger.neighbourLockUntilFrame(held, squads, now + 1);
        String firstEpisode = SquadDecisionLogger.suppressionEpisode(SquadLock.NEIGHBOUR_FIGHT, first, retreat,
                DecisionPath.SIM_RETREAT);
        String sameEpisode = SquadDecisionLogger.suppressionEpisode(SquadLock.NEIGHBOUR_FIGHT,
                SquadDecisionLogger.neighbourLockUntilFrame(held, squads, now + 5), retreat, DecisionPath.SIM_RETREAT);
        neighbour.startFightLock(now + 10);
        String renewedEpisode = SquadDecisionLogger.suppressionEpisode(SquadLock.NEIGHBOUR_FIGHT,
                SquadDecisionLogger.neighbourLockUntilFrame(held, squads, now + 11), retreat,
                DecisionPath.SIM_RETREAT);

        assertEquals(neighbour.getFightLockedUntilFrame() - 10, first);
        assertEquals(firstEpisode, sameEpisode);
        assertFalse(firstEpisode.equals(renewedEpisode));
    }

    @Test
    void aNeighbourHoldOverridesARetreatReadOfAFightSquadOnly() {
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.NEIGHBOUR_FIGHT,
                unit.squad.CombatSimulator.CombatResult.RETREAT));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.NEIGHBOUR_FIGHT,
                unit.squad.CombatSimulator.CombatResult.ENGAGE));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.NEIGHBOUR_FIGHT,
                unit.squad.CombatSimulator.CombatResult.RETREAT));
    }
}
