package strategy.buildorder;

import bwapi.UnitType;
import info.TechProgression;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanComparator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rush reaction declares an emergency that only a Spawning Pool can answer: every zergling and
 * colony it asks for waits on one. The shared defence path therefore queues the pool itself when
 * no pool is standing or planned, and leaves a pool the build order already claimed alone.
 */
class EmergencyPoolTest {

    private static final Path BUILD_ORDER_SOURCE = Paths.get("src", "main", "java", "strategy", "buildorder", "BuildOrder.java");

    private static final String PLAN_DEFENSE = "public List<Plan> planDefense(GameState gameState)";

    private static final String PLAN_STATIC_DEFENSE = "private Set<Plan> planStaticDefense(GameState gameState)";

    private static final int FRAMES = 30;

    private static final int HATCHERY_FRAME = 2044;

    private static final int EXTRACTOR_FRAME = 3077;

    @Test
    void aRushWithRoomForAPoolAsksForOne() {
        assertTrue(BuildOrder.shouldPlanEmergencyPool(true, true));
    }

    @Test
    void aRushWithAPoolStandingOrPlannedDoesNotAskForAnother() {
        assertFalse(BuildOrder.shouldPlanEmergencyPool(true, false));
    }

    @Test
    void noRushNoEmergencyPool() {
        assertFalse(BuildOrder.shouldPlanEmergencyPool(false, true));
        assertFalse(BuildOrder.shouldPlanEmergencyPool(false, false));
    }

    @Test
    void aHatchFirstBuildWithNoPoolGetsOneWhenRushed() {
        assertTrue(BuildOrder.shouldPlanEmergencyPool(true, new TechProgression().canPlanPool()));
    }

    @Test
    void anOpenersOwnPlannedPoolIsLeftAlone() {
        TechProgression techProgression = new TechProgression();
        techProgression.setPlannedSpawningPool(true);

        assertFalse(BuildOrder.shouldPlanEmergencyPool(true, techProgression.canPlanPool()));
    }

    @Test
    void aStandingPoolIsLeftAlone() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);

        assertFalse(BuildOrder.shouldPlanEmergencyPool(true, techProgression.canPlanPool()));
    }

    @Test
    void theEmergencyPoolIsQueuedOnceAndClosesTheBuildOrdersOwnPoolGate() {
        TechProgression techProgression = new TechProgression();
        int pools = 0;
        for (int frame = 0; frame < FRAMES; frame++) {
            if (BuildOrder.shouldPlanEmergencyPool(true, techProgression.canPlanPool())) {
                pools++;
                techProgression.setPlannedSpawningPool(true);
            }
        }

        assertEquals(1, pools);
        assertFalse(techProgression.canPlanPool());
    }

    @Test
    void theEmergencyPoolOutranksTheHatchFirstQueueAndOnlyRisesUnderTheRushReaction() {
        PlanComparator comparator = new PlanComparator();
        Plan pool = new BuildingPlan(UnitType.Zerg_Spawning_Pool, BuildOrder.EMERGENCY_DEFENSE_PRIORITY);

        assertTrue(comparator.compare(pool, new BuildingPlan(UnitType.Zerg_Hatchery, HATCHERY_FRAME)) < 0);
        assertTrue(comparator.compare(pool, new BuildingPlan(UnitType.Zerg_Extractor, EXTRACTOR_FRAME)) < 0);
        assertTrue(BuildOrder.EMERGENCY_DEFENSE_PRIORITY < BuildOrder.SPAWNING_POOL_PRIORITY);
        assertTrue(BuildOrder.EMERGENCY_DEFENSE_PRIORITY > 0);
    }

    @Test
    void planDefenseQueuesTheEmergencyPoolForEitherRushAtEmergencyPriority() throws IOException {
        String body = planDefenseBody();

        assertTrue(body.contains("gameState.isEarlyRushed() || gameState.isScvRushed()"), body);
        assertTrue(body.contains("shouldPlanEmergencyPool(rushed, gameState.getTechProgression().canPlanPool())"), body);
        assertTrue(body.contains("this.planSpawningPool(gameState)"), body);
        assertTrue(body.contains("poolPlan.setPriority(EMERGENCY_DEFENSE_PRIORITY)"), body);
        assertTrue(body.indexOf("shouldPlanEmergencyPool(") < body.indexOf("if (!gameState.isEarlyRushed())"), body);
    }

    private static String planDefenseBody() throws IOException {
        String source = new String(Files.readAllBytes(BUILD_ORDER_SOURCE), StandardCharsets.UTF_8);
        int start = source.indexOf(PLAN_DEFENSE);
        int end = source.indexOf(PLAN_STATIC_DEFENSE);
        assertTrue(start >= 0 && end > start, BUILD_ORDER_SOURCE.toString());
        return source.substring(start, end);
    }
}
