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

class GameStateTest {

    private static final int PRIORITY = 2;

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
}
