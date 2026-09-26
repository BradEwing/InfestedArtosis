package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class LurkerHoldTest {

    @Test
    void aLurkerHitInsideATanksReachIsSentOut() {
        assertEquals(LurkerHold.HIT, LurkerHold.reason(false, true, false, false));
    }

    @Test
    void aLurkerRetreatingInsideFixedFireIsSentOut() {
        assertEquals(LurkerHold.RETREAT, LurkerHold.reason(false, false, true, false));
    }

    @Test
    void aLurkerOutOfFireIsLeftAlone() {
        assertNull(LurkerHold.reason(false, false, false, false));
    }

    @Test
    void aHoldingLurkerKeepsItsPointWhileTheFireStaysOffIt() {
        assertNull(LurkerHold.reason(false, true, true, true));
    }

    @Test
    void aHoldingLurkerMovesWhenTheFireMovesOntoItsPoint() {
        assertEquals(LurkerHold.MOVED, LurkerHold.reason(true, false, false, true));
    }

    @Test
    void aSquadFightingOnAnEngageVerdictIsCommitting() {
        assertTrue(SquadManager.isCommitting(SquadStatus.FIGHT, false, ENGAGE));
    }

    @Test
    void aSquadUnderItsFightLockIsCommitting() {
        assertTrue(SquadManager.isCommitting(SquadStatus.FIGHT, true, ADVANCE));
    }

    @Test
    void aBlindAdvanceIsNotCommitting() {
        assertFalse(SquadManager.isCommitting(SquadStatus.FIGHT, false, ADVANCE));
        assertFalse(SquadManager.isCommitting(SquadStatus.FIGHT, false, null));
    }

    @Test
    void aSquadNotFightingIsNotCommitting() {
        assertFalse(SquadManager.isCommitting(SquadStatus.RETREAT, false, ENGAGE));
        assertFalse(SquadManager.isCommitting(SquadStatus.CONTAIN, true, RETREAT));
    }
}
