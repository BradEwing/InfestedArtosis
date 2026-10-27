package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import config.Config;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.SpeedlingReaction;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedlingReactionsTest {

    private static final int BAR = SpeedlingAllIn.SURPLUS_MINERALS;

    private static final int NEVER = Integer.MAX_VALUE / 2;

    @Test
    void noLarvaQueuedZerglingsAndABankAtTheBarIsLarvaBound() {
        assertTrue(SpeedlingReactions.isLarvaBound(0, 3, BAR, 0, BAR));
    }

    @Test
    void aFreeLarvaMeansTheBuildIsNotLarvaBound() {
        assertFalse(SpeedlingReactions.isLarvaBound(1, 3, 600, 0, BAR));
    }

    @Test
    void withNoZerglingPlanWaitingTheBuildIsNotLarvaBound() {
        assertFalse(SpeedlingReactions.isLarvaBound(0, 0, 600, 0, BAR));
    }

    @Test
    void aBankBelowTheBarIsNotLarvaBound() {
        assertFalse(SpeedlingReactions.isLarvaBound(0, 3, BAR - 1, 0, BAR));
    }

    @Test
    void aHatcheryAlreadyOnTheWayIsNotAskedForTwice() {
        assertFalse(SpeedlingReactions.isLarvaBound(0, 3, 600, 1, BAR));
    }

    @Test
    void zeroLarvaQueuedZerglingsAndMineralsAboveTheBarPlanTheThirdHatchery() {
        boolean larvaBound = SpeedlingReactions.isLarvaBound(0, 4, 450, 0, BAR);

        assertTrue(SpeedlingAllIn.shouldPlanHatchery(2, 40, larvaBound));
    }

    @Test
    void theUnreservedBankAloneStillCannotPlanTheThirdHatchery() {
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(2, 40));
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(2, 40, false));
    }

    @Test
    void theLarvaBoundSignalStillStopsAtTheHatcheryCeiling() {
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.MAX_HATCHERIES, 40, true));
    }

    @Test
    void enemyGroundUnitsInOurBaseStopTheDroneBelowTheFloor() {
        int belowFloor = SpeedlingAllIn.DRONE_TARGET_ONE_BASE - 3;

        assertTrue(SpeedlingAllIn.shouldPlanDrone(belowFloor, SpeedlingAllIn.DRONE_TARGET_ONE_BASE, 0, true, false));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(belowFloor, SpeedlingAllIn.DRONE_TARGET_ONE_BASE, 0, true, false,
                true));
    }

    @Test
    void aFloorThatIsNotSuspendedPlansTheDroneAsMainDoes() {
        for (int drones = 0; drones <= 14; drones++) {
            for (int zerglings : new int[] {0, 12}) {
                for (boolean owes : new boolean[] {false, true}) {
                    assertEquals(SpeedlingAllIn.shouldPlanDrone(drones, 12, zerglings, true, owes),
                            SpeedlingAllIn.shouldPlanDrone(drones, 12, zerglings, true, owes, false));
                }
            }
        }
    }

    @Test
    void theFloorIsSuspendedWhileEnemiesAreVisibleAndForAWhileAfter() {
        assertTrue(SpeedlingReactions.droneFloorSuspended(2, NEVER));
        assertTrue(SpeedlingReactions.droneFloorSuspended(0, SpeedlingReactions.DRONE_FLOOR_LINGER_FRAMES - 1));
        assertFalse(SpeedlingReactions.droneFloorSuspended(0, SpeedlingReactions.DRONE_FLOOR_LINGER_FRAMES));
        assertFalse(SpeedlingReactions.droneFloorSuspended(0, NEVER));
    }

    @Test
    void colonyPlansAreTaggedAndOtherPlansAreNot() {
        Plan creep = new BuildingPlan(UnitType.Zerg_Creep_Colony, 1);
        Plan sunken = new BuildingPlan(UnitType.Zerg_Sunken_Colony, 1);
        Plan pool = new BuildingPlan(UnitType.Zerg_Spawning_Pool, 1);
        Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, 1);
        List<Plan> plans = Arrays.asList(creep, sunken, pool, zergling);

        SpeedlingReactions.tagSunkenPlans(plans);

        assertEquals(SpeedlingReaction.SPEEDLING_P_SUNKEN, creep.getSpeedlingReaction());
        assertEquals(SpeedlingReaction.SPEEDLING_P_SUNKEN, sunken.getSpeedlingReaction());
        assertNull(pool.getSpeedlingReaction());
        assertNull(zergling.getSpeedlingReaction());
    }

    @Test
    void theReactionsAreOnByDefaultAndApplyOnlyAgainstProtoss() {
        assertTrue(Config.speedlingPReactions);
        assertTrue(SpeedlingAllIn.reactionsActive(true, Race.Protoss));
        assertFalse(SpeedlingAllIn.reactionsActive(true, Race.Terran));
        assertFalse(SpeedlingAllIn.reactionsActive(true, Race.Zerg));
        assertFalse(SpeedlingAllIn.reactionsActive(true, Race.Unknown));
    }

    @Test
    void withTheSwitchOffNoReactionAppliesAndTheDecisionsAreMains() {
        assertFalse(SpeedlingAllIn.reactionsActive(false, Race.Protoss));
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(2, 40, false));
        assertEquals(SpeedlingAllIn.shouldPlanHatchery(3, 350), SpeedlingAllIn.shouldPlanHatchery(3, 350, false));
        assertEquals("OBSERVED_SUSPENDED", SpeedlingAllIn.droneFloorLabel(true, false));
        assertEquals("SUSPENDED", SpeedlingAllIn.droneFloorLabel(true, true));
        assertEquals("RESUMED", SpeedlingAllIn.droneFloorLabel(false, true));
    }
}
