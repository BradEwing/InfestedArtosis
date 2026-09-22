package unit.squad;

import org.junit.jupiter.api.Test;
import telemetry.DecisionPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class SquadDecisionPathTest {

    @Test
    void aMeasuredVerdictNamesItself() {
        assertEquals(DecisionPath.SIM_ADVANCE, SquadManager.requestPath(false, ADVANCE));
        assertEquals(DecisionPath.SIM_ENGAGE, SquadManager.requestPath(false, ENGAGE));
        assertEquals(DecisionPath.SIM_RETREAT, SquadManager.requestPath(false, RETREAT));
    }

    @Test
    void nothingDetectedNamesTheMarchWhateverTheVerdict() {
        assertEquals(DecisionPath.NO_VISION_MARCH, SquadManager.requestPath(true, ADVANCE));
        assertEquals(DecisionPath.NO_VISION_MARCH, SquadManager.requestPath(true, ENGAGE));
        assertEquals(DecisionPath.NO_VISION_MARCH, SquadManager.requestPath(true, RETREAT));
    }

    @Test
    void anUnevaluatedVerdictNamesNoBranch() {
        assertEquals(DecisionPath.NONE, SquadManager.requestPath(false, null));
    }
}
