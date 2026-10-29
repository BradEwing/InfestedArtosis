package telemetry;

import org.junit.jupiter.api.Test;
import unit.squad.SquadStatus;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class FightBandTelemetryTest {

    @Test
    void aBandHeldEngageOverridesTheRetreatTheSingleCutAskedFor() {
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT_BAND, ENGAGE));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT_BAND, RETREAT));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.FIGHT_BAND, ENGAGE));
    }

    @Test
    void theBandLocksOverrideARetreatOfAFightSquad() {
        for (SquadLock lock : new SquadLock[] {SquadLock.FIGHT_LOCK_BAND, SquadLock.COMMIT_WINDOW}) {
            assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, lock, RETREAT), lock.name());
            assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, lock, ENGAGE), lock.name());
        }
    }

    @Test
    void aBlindTurnBackHoldOverridesTheAdvanceOfARetreatingSquad() {
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.BLIND_TURN_BACK, ADVANCE));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.BLIND_TURN_BACK, RETREAT));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.BLIND_TURN_BACK, ADVANCE));
    }

    @Test
    void theHoldThresholdAndContactAreTheLastColumnsAndUnevaluatedWithoutASnapshot() {
        String[] columns = SquadDecisionLogger.HEADER.split(",", -1);
        SquadDecision context = new SquadDecision();

        assertEquals("sim_hold_threshold", columns[columns.length - 2]);
        assertEquals("band_contact", columns[columns.length - 1]);
        assertEquals(Arrays.asList(Csv.format(SquadDecision.NOT_EVALUATED), String.valueOf(SquadDecision.NOT_EVALUATED)),
                SquadDecisionLogger.holdCells(context));
        context.setHoldThreshold(1.0);
        context.setInContact(1);
        assertEquals(Arrays.asList(Csv.format(1.0), "1"), SquadDecisionLogger.holdCells(context));
    }

    @Test
    void theRetreatThresholdFollowsTheSiegeBandColumnsAndIsUnevaluatedWithoutASnapshot() {
        String[] columns = SquadDecisionLogger.HEADER.split(",", -1);
        SquadDecision context = new SquadDecision();

        assertEquals("sim_retreat_threshold", columns[columns.length - 3]);
        assertEquals(Collections.singletonList(Csv.format(SquadDecision.NOT_EVALUATED)),
                SquadDecisionLogger.retreatThresholdCells(context));
        context.setRetreatThreshold(0.7);
        assertEquals(Collections.singletonList(Csv.format(0.7)), SquadDecisionLogger.retreatThresholdCells(context));
    }
}
