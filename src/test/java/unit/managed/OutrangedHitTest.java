package unit.managed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutrangedHitTest {

    private static final int FULL = 35;
    private static final int HIT = 29;
    private static final boolean NOTHING_IN_RANGE = false;
    private static final boolean IN_MELEE = true;

    @Test
    void aContainingLingHitWithNothingWithinItsOwnRangeIsOutranged() {
        assertTrue(ManagedUnit.isOutrangedHit(FULL, HIT, UnitRole.CONTAIN, NOTHING_IN_RANGE));
    }

    @Test
    void aLingInMeleeIsFightingBackNotOutranged() {
        assertFalse(ManagedUnit.isOutrangedHit(FULL, HIT, UnitRole.CONTAIN, IN_MELEE));
    }

    @Test
    void regenerationIsNotAHit() {
        assertFalse(ManagedUnit.isOutrangedHit(HIT, HIT + 1, UnitRole.CONTAIN, NOTHING_IN_RANGE));
        assertFalse(ManagedUnit.isOutrangedHit(HIT, HIT, UnitRole.CONTAIN, NOTHING_IN_RANGE));
    }

    @Test
    void everyRoleThatStandsOrWalksInFireCanBeOutranged() {
        for (UnitRole role : new UnitRole[] {UnitRole.CONTAIN, UnitRole.RALLY, UnitRole.FIGHT, UnitRole.RUNBY}) {
            assertTrue(ManagedUnit.isOutrangedHit(FULL, HIT, role, NOTHING_IN_RANGE), role.name());
        }
        for (UnitRole role : new UnitRole[] {UnitRole.GATHER, UnitRole.RETREAT, UnitRole.BUILD, UnitRole.SCOUT}) {
            assertFalse(ManagedUnit.isOutrangedHit(FULL, HIT, role, NOTHING_IN_RANGE), role.name());
        }
    }

    @Test
    void aFightingUnitClosingOnItsTargetKeepsClosing() {
        assertFalse(ManagedUnit.evadesOutrangedHit(UnitRole.FIGHT, true));
        assertTrue(ManagedUnit.evadesOutrangedHit(UnitRole.FIGHT, false));
    }

    @Test
    void containingRallyingAndRunningByUnitsStepOut() {
        assertTrue(ManagedUnit.evadesOutrangedHit(UnitRole.CONTAIN, false));
        assertTrue(ManagedUnit.evadesOutrangedHit(UnitRole.RALLY, true));
        assertTrue(ManagedUnit.evadesOutrangedHit(UnitRole.RUNBY, false));
    }

    @Test
    void aUnitOrderedToRetreatThisFrameIsLeftToTheRetreat() {
        assertFalse(ManagedUnit.evadesOutrangedHit(UnitRole.RETREAT, false));
    }
}
