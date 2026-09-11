package info;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The count that holds the hatchery request while a plan is outstanding. It reads the production
 * queue and the three stage sets, because a hatchery plan leaves the queue on the frame it is
 * created and the queue alone would read zero from then on.
 */
class HatcheryPlanCountTest {

    private static final int EXPANSION = 0;

    private static final int MACRO = 1;

    private static final boolean EXPANSION_KIND = false;

    private static final boolean MACRO_KIND = true;

    private static Plan hatchery(boolean macroHatchery) {
        Plan plan = new BuildingPlan(UnitType.Zerg_Hatchery, 1);
        plan.setMacroHatchery(macroHatchery);
        return plan;
    }

    private static Set<Plan> setOf(Plan... plans) {
        return new HashSet<>(Arrays.asList(plans));
    }

    private static List<Plan> none() {
        return Collections.emptyList();
    }

    @Test
    void anExpansionPlanIsOutstandingForTheExpansionRequestOnly() {
        Plan plan = hatchery(EXPANSION_KIND);

        assertTrue(GameState.isOutstandingHatcheryPlan(plan, EXPANSION_KIND));
        assertFalse(GameState.isOutstandingHatcheryPlan(plan, MACRO_KIND));
    }

    @Test
    void aMacroHatcheryPlanIsOutstandingForTheMacroRequestOnly() {
        Plan plan = hatchery(MACRO_KIND);

        assertTrue(GameState.isOutstandingHatcheryPlan(plan, MACRO_KIND));
        assertFalse(GameState.isOutstandingHatcheryPlan(plan, EXPANSION_KIND));
    }

    /**
     * GameState.cancelPlan leaves a cancelled plan in plansScheduled, so set membership does not
     * say whether the bot is still committed to it. The plan state does.
     */
    @Test
    void aCancelledPlanIsNoLongerOutstanding() {
        Plan plan = hatchery(EXPANSION_KIND);
        plan.setState(PlanState.CANCELLED);

        assertFalse(GameState.isOutstandingHatcheryPlan(plan, EXPANSION_KIND));
    }

    @Test
    void aPlanForAnotherBuildingIsNotAHatcheryPlan() {
        Plan lair = new BuildingPlan(UnitType.Zerg_Lair, 1);

        assertFalse(GameState.isOutstandingHatcheryPlan(lair, EXPANSION_KIND));
        assertFalse(GameState.isOutstandingHatcheryPlan(lair, MACRO_KIND));
    }

    @Test
    void aUnitPlanIsNotAHatcheryPlan() {
        Plan drone = new UnitPlan(UnitType.Zerg_Drone, 1);

        assertFalse(GameState.isOutstandingHatcheryPlan(drone, EXPANSION_KIND));
    }

    /**
     * Every stage contributes. Dropping any one term re-opens a window in which the request
     * reads zero while a hatchery is outstanding, which is the defect this ticket fixes.
     */
    @Test
    void everyStageCounts() {
        assertEquals(1, GameState.countHatcheryPlans(EXPANSION_KIND,
                setOf(hatchery(EXPANSION_KIND)), none(), none(), none()));
        assertEquals(1, GameState.countHatcheryPlans(EXPANSION_KIND,
                none(), setOf(hatchery(EXPANSION_KIND)), none(), none()));
        assertEquals(1, GameState.countHatcheryPlans(EXPANSION_KIND,
                none(), none(), setOf(hatchery(EXPANSION_KIND)), none()));
        assertEquals(1, GameState.countHatcheryPlans(EXPANSION_KIND,
                none(), none(), none(), setOf(hatchery(EXPANSION_KIND))));
    }

    @Test
    void theStagesSum() {
        int count = GameState.countHatcheryPlans(EXPANSION_KIND,
                setOf(hatchery(EXPANSION_KIND)),
                setOf(hatchery(EXPANSION_KIND)),
                setOf(hatchery(EXPANSION_KIND)),
                setOf(hatchery(EXPANSION_KIND)));

        assertEquals(4, count);
    }

    /**
     * The frame the defect was found on. The plan left the queue on its enqueue frame and landed
     * in plansBuilding, so a queue-only count read zero and the request fired again.
     */
    @Test
    void aPlanThatLeftTheQueueForTheBuildingSetStillCounts() {
        Plan walking = hatchery(EXPANSION_KIND);

        assertEquals(1, GameState.countHatcheryPlans(EXPANSION_KIND, none(), none(), setOf(walking), none()));
    }

    @Test
    void theKindsDoNotCountEachOther() {
        Set<Plan> scheduled = setOf(hatchery(EXPANSION_KIND), hatchery(MACRO_KIND));

        int[] counts = new int[2];
        counts[EXPANSION] = GameState.countHatcheryPlans(EXPANSION_KIND, none(), scheduled, none(), none());
        counts[MACRO] = GameState.countHatcheryPlans(MACRO_KIND, none(), scheduled, none(), none());

        assertEquals(1, counts[EXPANSION]);
        assertEquals(1, counts[MACRO]);
    }

    @Test
    void nothingOutstandingCountsZero() {
        assertEquals(0, GameState.countHatcheryPlans(EXPANSION_KIND, none(), none(), none(), none()));
    }
}
