package unit.squad;

import org.junit.jupiter.api.Test;
import unit.managed.Lurker;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LurkerWithdrawTest {

    @Test
    void aBurrowedContainingLurkerHitWithNothingInReachWithdraws() {
        boolean hit = ManagedUnit.isOutrangedHit(100, 80, UnitRole.CONTAIN, false);

        assertTrue(hit);
        assertTrue(SquadManager.evadeGateOpen(false, false, Lurker.canWithdraw(UnitRole.CONTAIN, true, true),
                UnitRole.CONTAIN, false));
    }

    @Test
    void aBurrowedLurkerThatCannotUnburrowDoesNotWithdraw() {
        assertFalse(SquadManager.evadeGateOpen(false, false, Lurker.canWithdraw(UnitRole.CONTAIN, true, false),
                UnitRole.CONTAIN, false));
    }

    @Test
    void aCollapsingLurkerDoesNotWithdraw() {
        assertFalse(SquadManager.evadeGateOpen(true, false, true, UnitRole.CONTAIN, false));
    }
}
