package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanComparator;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NineHatchTest {

    private static final int NINE_DRONE_SUPPLY = 18;

    private static final int NINE_DRONES = 9;

    private static final int ONE_BASE = 1;

    private static final int TWO_BASES = 2;

    private static final int FRAME = 1800;

    /**
     * Stands in for the plan factories so the real step walk runs without a live game. The
     * natural is either queued or refused, as planNewBase refuses it under the expansion backoff,
     * a reaction, a lost builder, or the enqueue cooldown.
     */
    private static final class ScriptedNineHatch extends NineHatch {
        private final boolean naturalQueueable;

        ScriptedNineHatch(boolean naturalQueueable) {
            this.naturalQueueable = naturalQueueable;
        }

        @Override
        protected Plan planNewBase(GameState gameState) {
            return naturalQueueable ? new BuildingPlan(UnitType.Zerg_Hatchery, FRAME) : null;
        }

        @Override
        protected Plan planUnit(GameState gameState, UnitType unitType) {
            return new UnitPlan(unitType, FRAME);
        }

        @Override
        protected Plan planSpawningPool(GameState gameState) {
            return new BuildingPlan(UnitType.Zerg_Spawning_Pool, poolPriority(FRAME));
        }
    }

    private static List<UnitType> planned(List<Plan> plans) {
        return plans.stream().map(Plan::getPlannedUnit).collect(Collectors.toList());
    }

    @Test
    void dronesUntilNine() {
        assertTrue(NineHatch.shouldPlanDrone(NINE_DRONES - 1, ONE_BASE));
        assertFalse(NineHatch.shouldPlanDrone(NINE_DRONES, ONE_BASE));
    }

    @Test
    void stopsDroningOnceTheNaturalIsCommitted() {
        assertFalse(NineHatch.shouldPlanDrone(NINE_DRONES - 1, TWO_BASES));
    }

    @Test
    void queuesTheHatcheryAtNineDrones() {
        assertTrue(NineHatch.shouldPlanHatchery(NINE_DRONE_SUPPLY, ONE_BASE));
        assertFalse(NineHatch.shouldPlanHatchery(NINE_DRONE_SUPPLY - 2, ONE_BASE));
    }

    @Test
    void queuesOnlyOneHatchery() {
        assertFalse(NineHatch.shouldPlanHatchery(NINE_DRONE_SUPPLY, TWO_BASES));
    }

    @Test
    void reachesTheHatcheryStepAtNineDronesOrACommittedNatural() {
        assertFalse(NineHatch.hatcheryStepReached(NINE_DRONE_SUPPLY - 2, ONE_BASE));
        assertTrue(NineHatch.hatcheryStepReached(NINE_DRONE_SUPPLY, ONE_BASE));
        assertTrue(NineHatch.hatcheryStepReached(NINE_DRONE_SUPPLY - 2, TWO_BASES));
    }

    @Test
    void waitsForTheHatcheryStepBeforeDroningBackToNine() {
        assertFalse(NineHatch.shouldDroneBackToNine(false, NINE_DRONES));
    }

    @Test
    void dronesBackToNineWhileTheBuilderWalks() {
        assertTrue(NineHatch.shouldDroneBackToNine(true, NINE_DRONES));
        assertFalse(NineHatch.shouldDroneBackToNine(true, NINE_DRONES + 1));
    }

    @Test
    void dronesBackToNineAfterTheMorph() {
        int eightDronesAndTheHatchery = NINE_DRONES - 1 + 1;
        assertTrue(NineHatch.shouldDroneBackToNine(true, eightDronesAndTheHatchery));
        assertFalse(NineHatch.shouldDroneBackToNine(true, eightDronesAndTheHatchery + 1));
    }

    /**
     * A drone lost after the replacement drone drops the count below 10 again, so the loss is
     * replaced too before the pool.
     */
    @Test
    void replacesADroneLostAfterTheReplacement() {
        int nineDronesAndTheHatcheryLessOneLost = NINE_DRONES + 1 - 1;
        assertTrue(NineHatch.shouldDroneBackToNine(true, nineDronesAndTheHatcheryLessOneLost));
        assertFalse(NineHatch.shouldPlanPool(nineDronesAndTheHatcheryLessOneLost, true));
    }

    @Test
    void queuesThePoolAfterTheDronesAreBackToNine() {
        assertFalse(NineHatch.shouldPlanPool(NINE_DRONES, true));
        assertTrue(NineHatch.shouldPlanPool(NINE_DRONES + 1, true));
    }

    @Test
    void queuesThePoolOnlyOnce() {
        TechProgression techProgression = new TechProgression();
        techProgression.setPlannedSpawningPool(true);
        assertFalse(NineHatch.shouldPlanPool(NINE_DRONES + 1, techProgression.canPlanPool()));
    }

    @Test
    void queuesTheNaturalTheDroneAndThePoolInOnePassAtNineDrones() {
        List<Plan> plans = new ScriptedNineHatch(true)
                .planSteps(null, NINE_DRONES, NINE_DRONE_SUPPLY, ONE_BASE, NINE_DRONES, true);

        assertEquals(Arrays.asList(UnitType.Zerg_Hatchery, UnitType.Zerg_Drone, UnitType.Zerg_Spawning_Pool),
                planned(plans));
    }

    /**
     * With the natural refused, one pass at 9 drones still queues the drone back to 9 and the
     * pool behind it, instead of returning nothing until the natural can be queued.
     */
    @Test
    void queuesTheDroneAndThePoolWhileTheNaturalIsBlocked() {
        List<Plan> plans = new ScriptedNineHatch(false)
                .planSteps(null, NINE_DRONES, NINE_DRONE_SUPPLY, ONE_BASE, NINE_DRONES, true);

        assertEquals(Arrays.asList(UnitType.Zerg_Drone, UnitType.Zerg_Spawning_Pool), planned(plans));
    }

    @Test
    void queuesThePoolWhileTheNaturalIsOnlyPlanned() {
        List<Plan> plans = new ScriptedNineHatch(true)
                .planSteps(null, NINE_DRONES + 1, NINE_DRONE_SUPPLY, TWO_BASES, NINE_DRONES + 1, true);

        assertEquals(Collections.singletonList(UnitType.Zerg_Spawning_Pool), planned(plans));
    }

    @Test
    void queuesOnlyTheDronesBeforeNine() {
        List<Plan> plans = new ScriptedNineHatch(true)
                .planSteps(null, NINE_DRONES - 1, NINE_DRONE_SUPPLY - 2, ONE_BASE, NINE_DRONES - 1, true);

        assertEquals(Collections.singletonList(UnitType.Zerg_Drone), planned(plans));
    }

    @Test
    void sortsThePoolBehindTheHatcheryQueuedOnTheSameFrame() {
        Plan hatchery = new BuildingPlan(UnitType.Zerg_Hatchery, FRAME);
        Plan pool = new BuildingPlan(UnitType.Zerg_Spawning_Pool, new NineHatch().poolPriority(FRAME));

        assertTrue(new PlanComparator().compare(hatchery, pool) < 0);
    }

    @Test
    void transitionsOnceThePoolIsCommitted() {
        assertFalse(NineHatch.openerComplete(0));
        assertTrue(NineHatch.openerComplete(1));
    }

    @Test
    void playsEveryKnownRace() {
        NineHatch opener = new NineHatch();
        assertTrue(opener.playsRace(Race.Protoss));
        assertTrue(opener.playsRace(Race.Terran));
        assertTrue(opener.playsRace(Race.Zerg));
        assertFalse(opener.playsRace(Race.Unknown));
        assertTrue(opener.isOpener());
    }
}
