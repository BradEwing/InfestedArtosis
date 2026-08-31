package telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.squad.CombatSimulator;
import unit.squad.GroundSquad;
import unit.squad.Squad;
import unit.squad.SquadStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadDecisionsTest {

    private final List<String> events = new ArrayList<>();

    private SquadDecisionSink recorder() {
        return new SquadDecisionSink() {
            @Override
            public void onSimEvaluated(Squad squad, CombatSimulator.CombatResult result, boolean retreatLocked,
                                       boolean fightLocked) {
                events.add("SIM:" + result + ":" + retreatLocked + ":" + fightLocked);
            }

            @Override
            public void onLockSuppressed(Squad squad, SquadLock lock) {
                events.add("LOCK:" + lock);
            }

            @Override
            public void onContainmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment,
                                               boolean entered) {
                events.add("CONTAIN:" + shouldContain + ":" + canBreakContainment + ":" + entered);
            }
        };
    }

    @AfterEach
    void clearSink() {
        SquadDecisions.clear();
    }

    @Test
    void dispatchIsANoOpWithoutASink() {
        Squad squad = new GroundSquad();

        SquadDecisions.simEvaluated(squad, CombatSimulator.CombatResult.ENGAGE, false, false);
        SquadDecisions.lockSuppressed(squad, SquadLock.FIGHT);
        SquadDecisions.containmentEvaluated(squad, true, false, true);

        assertTrue(events.isEmpty());
    }

    @Test
    void registeredSinkReceivesEveryDecisionInput() {
        SquadDecisions.register(recorder());
        Squad squad = new GroundSquad();

        SquadDecisions.simEvaluated(squad, CombatSimulator.CombatResult.RETREAT, true, false);
        SquadDecisions.lockSuppressed(squad, SquadLock.RETREAT);
        SquadDecisions.containmentEvaluated(squad, true, false, true);

        assertEquals(3, events.size());
        assertEquals("SIM:RETREAT:true:false", events.get(0));
        assertEquals("LOCK:RETREAT", events.get(1));
        assertEquals("CONTAIN:true:false:true", events.get(2));
    }

    @Test
    void clearStopsDispatch() {
        SquadDecisions.register(recorder());
        SquadDecisions.clear();

        SquadDecisions.lockSuppressed(new GroundSquad(), SquadLock.FIGHT);

        assertTrue(events.isEmpty());
    }

    @Test
    void aLockOnlySuppressesWhenTheVerdictAsksForTheOtherStatus() {
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.RETREAT,
                CombatSimulator.CombatResult.ENGAGE));
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.RETREAT,
                CombatSimulator.CombatResult.ADVANCE));
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT,
                CombatSimulator.CombatResult.RETREAT));

        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.RETREAT,
                CombatSimulator.CombatResult.RETREAT));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT,
                CombatSimulator.CombatResult.ENGAGE));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT,
                CombatSimulator.CombatResult.ADVANCE));
    }

    @Test
    void anUnevaluatedVerdictNeverCountsAsSuppression() {
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.RETREAT, null));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT, null));
    }
}
