package unit.managed;

import bwapi.Order;
import bwapi.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackMoveTest {

    private static final Position TARGET = new Position(1000, 1000);

    @Test
    void anAttackMoveIsIssuedWhenTheUnitHasAnotherOrder() {
        assertTrue(ManagedUnit.needsAttackMove(Order.AttackUnit, TARGET, false, TARGET));
        assertTrue(ManagedUnit.needsAttackMove(Order.Move, TARGET, false, TARGET));
        assertTrue(ManagedUnit.needsAttackMove(Order.PlayerGuard, null, false, TARGET));
    }

    @Test
    void anAttackMoveAlreadyHeadedForTheTargetIsNotReissued() {
        Position withinReissue = new Position(TARGET.getX() + ManagedUnit.ATTACK_MOVE_REISSUE_DISTANCE, TARGET.getY());

        assertFalse(ManagedUnit.needsAttackMove(Order.AttackMove, TARGET, false, TARGET));
        assertFalse(ManagedUnit.needsAttackMove(Order.AttackMove, withinReissue, false, TARGET));
    }

    @Test
    void anAttackMoveIsReissuedOnceTheTargetHasMovedAway() {
        Position stale = new Position(TARGET.getX() + ManagedUnit.ATTACK_MOVE_REISSUE_DISTANCE + 1, TARGET.getY());

        assertTrue(ManagedUnit.needsAttackMove(Order.AttackMove, stale, false, TARGET));
    }

    @Test
    void aUnitThatIsAttackingIsLeftToIt() {
        assertFalse(ManagedUnit.needsAttackMove(Order.AttackUnit, TARGET, true, TARGET));
        assertFalse(ManagedUnit.needsAttackMove(Order.AttackMove, null, true, TARGET));
    }
}
