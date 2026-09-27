package info;

import bwapi.UnitType;
import macro.HatcheryCapacity;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanState;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GameState's floating-minerals request: unreserved minerals against 350 per unfinished hatchery
 * (in-flight plan or under construction) plus 350, after 5:00.
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

    /**
     * Game LYRGH0GO: a macro Hatchery's drone morphed at frame 13846 and, with that Hatchery still
     * building and 358 unreserved, the request asked for a fourth base at 14532. The plan leaves
     * the production system when its drone morphs, and the hatchery it became holds the bar at 700
     * until it finishes.
     */
    @Test
    void aHatcheryUnderConstructionHoldsTheBarItsPlanRaised() {
        Plan macroHatchery = hatchery(true);
        Set<Plan> morphing = setOf(macroHatchery);
        int beforeMorph = GameState.unfinishedHatcheries(
                GameState.countHatcheryPlans(none(), none(), none(), morphing), 0, 0);

        morphing.remove(macroHatchery);
        macroHatchery.setState(PlanState.COMPLETE);
        int afterMorph = GameState.unfinishedHatcheries(
                GameState.countHatcheryPlans(none(), none(), none(), morphing), 0, 1);

        assertEquals(1, beforeMorph);
        assertEquals(1, afterMorph);
        assertFalse(GameState.isFloatingMinerals(bank(358), afterMorph, MIDGAME));
        assertFalse(GameState.isFloatingMinerals(bank(700), afterMorph, MIDGAME));
        assertTrue(GameState.isFloatingMinerals(bank(701), afterMorph, MIDGAME));
    }

    /**
     * Hatcheries under construction count whichever kind they are, so an expansion going up holds a
     * macro-driven bar and a macro hatchery going up holds the expansion request.
     */
    @Test
    void bothKindsUnderConstructionRaiseTheBar() {
        int unfinished = GameState.unfinishedHatcheries(1, 1, 1);

        assertEquals(3, unfinished);
        assertFalse(GameState.isFloatingMinerals(bank(1400), unfinished, MIDGAME));
        assertTrue(GameState.isFloatingMinerals(bank(1401), unfinished, MIDGAME));
    }

    @Test
    void aFinishedHatcheryReleasesTheBar() {
        assertFalse(GameState.isFloatingMinerals(bank(358), GameState.unfinishedHatcheries(0, 1, 0), MIDGAME));
        assertTrue(GameState.isFloatingMinerals(bank(358), GameState.unfinishedHatcheries(0, 0, 0), MIDGAME));
    }

    /**
     * One expansion walked through every state it passes on the way to a finished hatchery. The bar
     * reads 700 at each: queued, scheduled, BUILDING while its drone walks to the site, MORPHING in
     * plansBuilding once the drone is ordered to build, MORPHING in plansMorphing after the morph
     * event, and the incomplete Zerg_Hatchery once the plan completes. It drops to 350 only when the
     * hatchery finishes.
     */
    @Test
    void anExpansionHoldsTheBarInEveryStateUntilItsHatcheryFinishes() {
        Plan expansion = hatchery(false);
        List<Plan> queued = new ArrayList<>(Collections.singletonList(expansion));
        Set<Plan> scheduled = new HashSet<>();
        Set<Plan> building = new HashSet<>();
        Set<Plan> morphing = new HashSet<>();
        assertEquals(700, bar(queued, scheduled, building, morphing, false, false));

        queued.remove(expansion);
        scheduled.add(expansion);
        expansion.setState(PlanState.SCHEDULE);
        assertEquals(700, bar(queued, scheduled, building, morphing, false, false));

        scheduled.remove(expansion);
        building.add(expansion);
        expansion.setState(PlanState.BUILDING);
        assertEquals(700, bar(queued, scheduled, building, morphing, false, false));

        expansion.setState(PlanState.MORPHING);
        assertEquals(700, bar(queued, scheduled, building, morphing, false, false));

        building.remove(expansion);
        morphing.add(expansion);
        assertEquals(700, bar(queued, scheduled, building, morphing, false, false));

        morphing.remove(expansion);
        expansion.setState(PlanState.COMPLETE);
        assertEquals(700, bar(queued, scheduled, building, morphing, true, false));

        assertEquals(350, bar(queued, scheduled, building, morphing, false, false));
    }

    /**
     * A builder lost on the way returns its plan to SCHEDULE, and the bar holds while a new drone is
     * found.
     */
    @Test
    void aPlanReturnedToScheduleByABuilderLossHoldsTheBar() {
        Plan expansion = hatchery(false);
        expansion.setState(PlanState.SCHEDULE);

        assertEquals(700, bar(none(), setOf(expansion), setOf(), setOf(), false, false));
    }

    /**
     * A completed plan left in plansScheduled is not counted, so the hatchery it became is counted
     * once, by the unit scan.
     */
    @Test
    void aCompletedPlanLeftInScheduledIsNotCountedTwice() {
        Plan expansion = hatchery(false);
        expansion.setState(PlanState.COMPLETE);

        assertEquals(700, bar(none(), setOf(expansion), setOf(), setOf(), true, false));
    }

    @Test
    void onlyAnIncompleteHatcheryIsUnderConstruction() {
        assertTrue(GameState.isHatcheryUnderConstruction(UnitType.Zerg_Hatchery, false, true, false));
        assertFalse(GameState.isHatcheryUnderConstruction(UnitType.Zerg_Hatchery, true, true, false));
        assertFalse(GameState.isHatcheryUnderConstruction(UnitType.Zerg_Drone, false, true, false));
        assertFalse(GameState.isHatcheryUnderConstruction(UnitType.Zerg_Lair, false, true, false));
    }

    @Test
    void aHatcheryOnABaseTileIsAnExpansionAndAnyOtherIsAMacroHatchery() {
        assertTrue(GameState.isHatcheryUnderConstruction(UnitType.Zerg_Hatchery, false, true, false));
        assertFalse(GameState.isHatcheryUnderConstruction(UnitType.Zerg_Hatchery, false, true, true));
        assertTrue(GameState.isHatcheryUnderConstruction(UnitType.Zerg_Hatchery, false, false, true));
        assertFalse(GameState.isHatcheryUnderConstruction(UnitType.Zerg_Hatchery, false, false, false));
    }

    private static int bar(Iterable<Plan> queued, Iterable<Plan> scheduled, Iterable<Plan> building,
            Iterable<Plan> morphing, boolean expansionGoingUp, boolean macroHatcheryGoingUp) {
        int expansions = GameState.isHatcheryUnderConstruction(UnitType.Zerg_Hatchery, !expansionGoingUp, true, false)
                ? 1 : 0;
        int macroHatcheries = GameState.isHatcheryUnderConstruction(UnitType.Zerg_Hatchery, !macroHatcheryGoingUp,
                false, true) ? 1 : 0;
        int unfinished = GameState.unfinishedHatcheries(
                GameState.countHatcheryPlans(queued, scheduled, building, morphing), expansions, macroHatcheries);
        return HatcheryCapacity.floatingMineralsBar(unfinished);
    }

    @Test
    void plansForOtherBuildingsDoNotRaiseTheBar() {
        Set<Plan> building = setOf(new BuildingPlan(UnitType.Zerg_Lair, 1), new BuildingPlan(UnitType.Zerg_Spire, 1));

        assertEquals(0, GameState.countHatcheryPlans(none(), none(), building, none()));
    }
}
