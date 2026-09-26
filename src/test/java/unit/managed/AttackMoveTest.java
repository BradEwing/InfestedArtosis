package unit.managed;

import bwapi.Order;
import bwapi.Position;
import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void anOverflowAttackMoveIsAimedPastTheTargetAlongTheApproachNotAtTheTarget() {
        Position attacker = new Position(TARGET.getX() - 300, TARGET.getY() - 400);

        Position destination = ManagedUnit.pastTarget(attacker, TARGET).toPosition(TARGET);

        assertNotEquals(TARGET, destination);
        assertEquals(ManagedUnit.OVERFLOW_PAST_DISTANCE, TARGET.getDistance(destination), 1.5);
        assertEquals(attacker.getDistance(TARGET) + ManagedUnit.OVERFLOW_PAST_DISTANCE,
                attacker.getDistance(destination), 1.5);
        assertTrue(destination.getX() > TARGET.getX() && destination.getY() > TARGET.getY());
    }

    @Test
    void anOverflowAttackMoveFromTheTargetsOwnPointAimsAtTheTarget() {
        assertEquals(TARGET, ManagedUnit.pastTarget(TARGET, TARGET).toPosition(TARGET));
    }

    @Test
    void theOverflowDestinationClearsTheReissueGuardAroundTheTarget() {
        assertTrue(ManagedUnit.OVERFLOW_PAST_DISTANCE > ManagedUnit.ATTACK_MOVE_REISSUE_DISTANCE);
    }

    @Test
    void anAttackOnAnotherEnemyAcquiredDuringTheAttackMoveIsLeftToRun() {
        TestUnits units = new TestUnits();
        Unit saturated = units.unit(UnitType.Terran_Marine);
        Unit acquired = units.unit(UnitType.Terran_Medic);

        assertTrue(ManagedUnit.autoAcquired(Order.AttackUnit, acquired, saturated));
    }

    @Test
    void theDirectAttackHeldBeforeOverflowIsReplacedByTheAttackMove() {
        TestUnits units = new TestUnits();
        Unit saturated = units.unit(UnitType.Terran_Marine);
        Unit other = units.unit(UnitType.Terran_Medic);

        assertFalse(ManagedUnit.autoAcquired(Order.AttackUnit, saturated, saturated));
        assertFalse(ManagedUnit.autoAcquired(Order.AttackUnit, null, saturated));
        assertFalse(ManagedUnit.autoAcquired(Order.AttackMove, other, saturated));
        assertFalse(ManagedUnit.autoAcquired(Order.Move, other, saturated));
    }

    @Test
    void aUnitThatIsAttackingIsLeftToIt() {
        assertFalse(ManagedUnit.needsAttackMove(Order.AttackUnit, TARGET, true, TARGET));
        assertFalse(ManagedUnit.needsAttackMove(Order.AttackMove, null, true, TARGET));
    }
}
