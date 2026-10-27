package telemetry;

import org.junit.jupiter.api.Test;
import unit.squad.SquadStatus;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void theRetreatThresholdIsTheLastColumnAndUnevaluatedWithoutASnapshot() {
        String[] columns = SquadDecisionLogger.HEADER.split(",", -1);
        SquadDecision context = new SquadDecision();

        assertEquals("sim_retreat_threshold", columns[columns.length - 1]);
        assertEquals(Collections.singletonList(Csv.format(SquadDecision.NOT_EVALUATED)),
                SquadDecisionLogger.retreatThresholdCells(context));
        context.setRetreatThreshold(0.7);
        assertEquals(Collections.singletonList(Csv.format(0.7)), SquadDecisionLogger.retreatThresholdCells(context));
    }
}
