package strategy.buildorder.terran;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerranBaseTest {

    private static final int DRONES_AT_POOL_COMPLETION = 10;
    private static final int UNSCOUTED_ZERGLING_FLOOR = TerranBase.UNSCOUTED_ZERGLINGS;
    private static final int SIEGE_TANKS = 6;
    private static final int VULTURES = 3;
    private static final int FACTORIES = 2;

    @Test
    void yieldsTheLarvaToZerglingsOnThePoolCompletionFrame() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 0, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void keepsYieldingWhileTheZerglingFloorIsUnmet() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 2, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void resumesDroningOnceTheZerglingFloorIsMet() {
        assertTrue(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void dronesFreelyWhenNoZerglingsAreOwed() {
        assertTrue(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 0, 0));
    }

    @Test
    void stopsTheEarlyDroneGateAtTheTarget() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(TerranBase.EARLY_DRONE_TARGET, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void keepsDroningWhenParkedDronesInflateTheLivingCount() {
        int gatheringDrones = TerranBase.EARLY_DRONE_TARGET - 1;
        int livingDrones = gatheringDrones + 6;

        assertFalse(TerranBase.shouldDroneBeforeZerglings(livingDrones, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
        assertTrue(TerranBase.shouldDroneBeforeZerglings(gatheringDrones, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void asksForNothingExtraAgainstAnEnemyWithNoMech() {
        assertEquals(0, TerranBase.mechDrivenZerglings(0, 0, 0, 0));
    }

    @Test
    void scalesTheTargetWithTheMechArmyWhenEveryBioTermIsZero() {
        int mechTerm = TerranBase.mechDrivenZerglings(SIEGE_TANKS, VULTURES, 0, FACTORIES);
        int target = UNSCOUTED_ZERGLING_FLOOR + mechTerm;

        assertEquals(SIEGE_TANKS * TerranBase.ZERGLINGS_PER_SIEGE_TANK
                + VULTURES * TerranBase.ZERGLINGS_PER_VULTURE, mechTerm);
        assertTrue(target > UNSCOUTED_ZERGLING_FLOOR);
        assertTrue(Math.min(TerranBase.MAX_ZERGLINGS, target) > TerranBase.MECH_ZERGLING_FLOOR);
    }

    @Test
    void growsWithEachMechUnitTypeItCanSee() {
        int tanks = TerranBase.mechDrivenZerglings(1, 0, 0, 0);
        int vultures = TerranBase.mechDrivenZerglings(0, 1, 0, 0);
        int goliaths = TerranBase.mechDrivenZerglings(0, 0, 1, 0);

        assertTrue(tanks > 0);
        assertTrue(vultures > 0);
        assertTrue(goliaths > 0);
        assertTrue(TerranBase.mechDrivenZerglings(2, 2, 2, 0) > TerranBase.mechDrivenZerglings(1, 1, 1, 0));
    }

    @Test
    void asksForZerglingsOffAScoutedFactoryBeforeItsUnitsAreSeen() {
        assertEquals(TerranBase.ZERGLINGS_PER_FACTORY * FACTORIES, TerranBase.mechDrivenZerglings(0, 0, 0, FACTORIES));
    }

    @Test
    void doesNotCountAFactoryOnTopOfTheUnitsItAlreadyBuilt() {
        int fromUnits = TerranBase.mechDrivenZerglings(SIEGE_TANKS, VULTURES, 0, 0);

        assertEquals(fromUnits, TerranBase.mechDrivenZerglings(SIEGE_TANKS, VULTURES, 0, FACTORIES));
    }
}
