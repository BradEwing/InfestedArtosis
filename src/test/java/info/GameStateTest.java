package info;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateTest {

    private static final int PRIORITY = 2;

    private static final int NONE = 0;

    private static final int ONE = 1;

    private static final boolean NO_RUSH_DELAY = false;

    private static final boolean NEEDS_LAIR = true;

    private static final boolean TECH_ALLOWS_LAIR = true;

    private static final boolean ENOUGH_HATCHERIES = true;

    private static Plan buildingPlan(UnitType unitType, PlanState state) {
        Plan plan = new BuildingPlan(unitType, PRIORITY);
        plan.setState(state);
        return plan;
    }

    private static Set<Plan> setOf(Plan... plans) {
        Set<Plan> set = new HashSet<>();
        for (Plan plan : plans) {
            set.add(plan);
        }
        return set;
    }

    @Test
    void countsABuildingPlanUnderConstruction() {
        Set<Plan> plans = setOf(buildingPlan(UnitType.Zerg_Spawning_Pool, PlanState.BUILDING));

        assertEquals(1, GameState.buildingPlanCount(plans, UnitType.Zerg_Spawning_Pool));
    }

    @Test
    void countsABuildingPlanInEveryStageItCanSitIn() {
        Set<Plan> plans = setOf(
                buildingPlan(UnitType.Zerg_Spawning_Pool, PlanState.PLANNED),
                buildingPlan(UnitType.Zerg_Spawning_Pool, PlanState.SCHEDULE),
                buildingPlan(UnitType.Zerg_Spawning_Pool, PlanState.BUILDING),
                buildingPlan(UnitType.Zerg_Spawning_Pool, PlanState.MORPHING));

        assertEquals(4, GameState.buildingPlanCount(plans, UnitType.Zerg_Spawning_Pool));
    }

    @Test
    void skipsACancelledBuildingPlan() {
        Set<Plan> plans = setOf(buildingPlan(UnitType.Zerg_Spawning_Pool, PlanState.CANCELLED));

        assertEquals(0, GameState.buildingPlanCount(plans, UnitType.Zerg_Spawning_Pool));
    }

    @Test
    void skipsACompletedBuildingPlan() {
        Set<Plan> plans = setOf(buildingPlan(UnitType.Zerg_Spawning_Pool, PlanState.COMPLETE));

        assertEquals(0, GameState.buildingPlanCount(plans, UnitType.Zerg_Spawning_Pool));
    }

    @Test
    void skipsABuildingPlanOfAnotherType() {
        Set<Plan> plans = setOf(buildingPlan(UnitType.Zerg_Hatchery, PlanState.BUILDING));

        assertEquals(0, GameState.buildingPlanCount(plans, UnitType.Zerg_Spawning_Pool));
    }

    @Test
    void skipsAUnitPlan() {
        Plan dronePlan = new UnitPlan(UnitType.Zerg_Drone, PRIORITY);
        dronePlan.setState(PlanState.BUILDING);

        assertEquals(0, GameState.buildingPlanCount(setOf(dronePlan), UnitType.Zerg_Drone));
    }

    @Test
    void countsAStructureUnderConstructionAsCommitted() {
        assertEquals(1, GameState.structureCount(Readiness.COMMITTED, NONE, ONE, NONE));
    }

    @Test
    void countsAStructureWithOnlyAPlanInFlightAsCommitted() {
        assertEquals(1, GameState.structureCount(Readiness.COMMITTED, NONE, NONE, ONE));
    }

    @Test
    void countsEveryStageOfAStructureAsCommitted() {
        assertEquals(3, GameState.structureCount(Readiness.COMMITTED, ONE, ONE, ONE));
    }

    @Test
    void countsNeitherAStructureUnderConstructionNorAPlanAsUsable() {
        assertEquals(0, GameState.structureCount(Readiness.USABLE, NONE, ONE, NONE));
        assertEquals(0, GameState.structureCount(Readiness.USABLE, NONE, NONE, ONE));
        assertEquals(0, GameState.structureCount(Readiness.USABLE, NONE, ONE, ONE));
    }

    @Test
    void countsAFinishedStructureAtEitherReadiness() {
        assertEquals(1, GameState.structureCount(Readiness.USABLE, ONE, NONE, NONE));
        assertEquals(1, GameState.structureCount(Readiness.COMMITTED, ONE, NONE, NONE));
    }

    /**
     * The Lair's Extractor term stands for gas income, so it stays on finished Extractors. An
     * Extractor under construction or still in a plan mines nothing, and a Lair queued against it
     * reserves 100 gas from a bank that cannot grow until the Extractor stands.
     */
    @Test
    void withholdsTheLairWhileTheExtractorIsStillBuilding() {
        int usableExtractors = GameState.structureCount(Readiness.USABLE, NONE, ONE, ONE);

        assertFalse(GameState.canPlanLair(NO_RUSH_DELAY, NEEDS_LAIR, TECH_ALLOWS_LAIR, ENOUGH_HATCHERIES, usableExtractors));
    }

    @Test
    void takesTheLairOnceTheExtractorStands() {
        int usableExtractors = GameState.structureCount(Readiness.USABLE, ONE, NONE, NONE);

        assertTrue(GameState.canPlanLair(NO_RUSH_DELAY, NEEDS_LAIR, TECH_ALLOWS_LAIR, ENOUGH_HATCHERIES, usableExtractors));
    }
}
