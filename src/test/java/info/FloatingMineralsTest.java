package info;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanState;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GameState's floating-minerals request: unreserved minerals against 350 per in-flight hatchery
 * plan plus 350, after 5:00.
 */
class FloatingMineralsTest {

    private static final Time MIDGAME = new Time(12, 0);

    private static ResourceCount bank(int minerals) {
        return new ResourceCount(null) {
            @Override
            public int availableMinerals() {
                return minerals - getReservedMinerals();
            }
        };
    }

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
    void firesAt351UnreservedWithNoHatcheryPlanned() {
        assertFalse(GameState.isFloatingMinerals(bank(350), 0, MIDGAME));
        assertTrue(GameState.isFloatingMinerals(bank(351), 0, MIDGAME));
    }

    /**
     * Game LXMXW0I6: seven completed hatcheries and 2112 banked at 20 minutes never cleared the
     * old 2800 bar. Completed hatcheries are not an input, so the bar is 350 with nothing planned.
     */
    @Test
    void theBarDoesNotReadCompletedHatcheries() {
        assertTrue(GameState.isFloatingMinerals(bank(2112), 0, new Time(20, 0)));
    }

    @Test
    void reservedMineralsLowerTheInput() {
        ResourceCount resourceCount = bank(351 + UnitType.Zerg_Evolution_Chamber.mineralPrice());
        assertTrue(GameState.isFloatingMinerals(resourceCount, 0, MIDGAME));

        resourceCount.reserveUnit(UnitType.Zerg_Evolution_Chamber);
        assertTrue(GameState.isFloatingMinerals(resourceCount, 0, MIDGAME));

        resourceCount.reserveUnit(UnitType.Zerg_Zergling);
        assertFalse(GameState.isFloatingMinerals(resourceCount, 0, MIDGAME));
    }

    @Test
    void reservationsBeyondTheBankNeverFire() {
        ResourceCount resourceCount = bank(UnitType.Zerg_Lair.mineralPrice());
        resourceCount.reserveUnit(UnitType.Zerg_Lair);
        resourceCount.reserveUnit(UnitType.Zerg_Spire);

        assertTrue(resourceCount.availableMinerals() < 0);
        assertFalse(GameState.isFloatingMinerals(resourceCount, 0, MIDGAME));
    }

    @Test
    void notBeforeFiveMinutes() {
        assertFalse(GameState.isFloatingMinerals(bank(5000), 0, new Time(5, 0)));
        assertTrue(GameState.isFloatingMinerals(bank(5000), 0, new Time(5, 0).add(new Time(1))));
    }

    /**
     * Every in-flight hatchery plan counts toward the bar, macro hatcheries as well as
     * expansions, at every stage the production system holds it in. A cancelled plan does not.
     */
    @Test
    void everyInFlightHatcheryPlanRaisesTheBar() {
        Plan cancelled = hatchery(false);
        cancelled.setState(PlanState.CANCELLED);
        List<Plan> queued = Collections.singletonList(hatchery(false));
        Set<Plan> scheduled = setOf(hatchery(true), cancelled);

        int planned = GameState.countHatcheryPlans(queued, scheduled, none(), none());

        assertEquals(2, planned);
        assertFalse(GameState.isFloatingMinerals(bank(1050), planned, MIDGAME));
        assertTrue(GameState.isFloatingMinerals(bank(1051), planned, MIDGAME));
    }

    @Test
    void plansForOtherBuildingsDoNotRaiseTheBar() {
        Set<Plan> building = setOf(new BuildingPlan(UnitType.Zerg_Lair, 1), new BuildingPlan(UnitType.Zerg_Spire, 1));

        assertEquals(0, GameState.countHatcheryPlans(none(), none(), building, none()));
    }
}
