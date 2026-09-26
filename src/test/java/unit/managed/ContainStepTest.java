package unit.managed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainStepTest {

    @Test
    void aWrappingFlankIsIssuedAnAttackMoveToItsPoint() {
        assertEquals(ManagedUnit.ContainStep.ATTACK_MOVE, ManagedUnit.containStep(false, 200, true));
    }

    @Test
    void aContainingUnitOnItsArcMovesToItsPoint() {
        assertEquals(ManagedUnit.ContainStep.MOVE, ManagedUnit.containStep(false, 200, false));
    }

    @Test
    void anEnemyInRangeIsAttackedWhateverTheWayToThePoint() {
        assertEquals(ManagedUnit.ContainStep.ATTACK_ENEMY, ManagedUnit.containStep(true, 200, true));
        assertEquals(ManagedUnit.ContainStep.ATTACK_ENEMY, ManagedUnit.containStep(true, 200, false));
    }

    @Test
    void aUnitAtItsPointHolds() {
        assertEquals(ManagedUnit.ContainStep.HOLD, ManagedUnit.containStep(false, 23, true));
        assertEquals(ManagedUnit.ContainStep.ATTACK_MOVE, ManagedUnit.containStep(false, 24, true));
        assertEquals(ManagedUnit.ContainStep.HOLD, ManagedUnit.containStep(false, 23, false));
    }
}
