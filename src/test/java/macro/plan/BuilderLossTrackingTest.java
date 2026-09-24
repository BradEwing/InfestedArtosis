package macro.plan;

import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.managed.BuilderStall;
import unit.managed.UnitRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderLossTrackingTest {

    private static final BuilderReading LOST =
            new BuilderReading(161, UnitRole.GATHER, "MoveToMinerals", 914, false);
    private static final BuilderReading WALKING = new BuilderReading(161, UnitRole.BUILD, "Move", 480, false);

    private static Plan den() {
        Plan plan = new BuildingPlan(UnitType.Zerg_Hydralisk_Den, 1, new TilePosition(40, 20));
        plan.setState(PlanState.SCHEDULE);
        return plan;
    }

    @Test
    void aBuilderAtTheArrivalDistanceIsInRange() {
        assertTrue(BuilderReading.isInRange(BuilderStall.ARRIVAL_DISTANCE));
        assertTrue(BuilderReading.isInRange(0));
    }

    @Test
    void aBuilderOnePixelBeyondTheArrivalDistanceIsNotInRange() {
        assertFalse(BuilderReading.isInRange(BuilderStall.ARRIVAL_DISTANCE + 1));
    }

    @Test
    void aLossPairsWithTheNextDispatchOfItsPlanOnce() {
        LostBuilders lostBuilders = new LostBuilders();
        Plan plan = den();
        lostBuilders.lost(plan, BuilderLossReason.STRAYED, LOST);

        LostBuilders.Loss loss = lostBuilders.takeOver(plan);

        assertSame(BuilderLossReason.STRAYED, loss.getReason());
        assertSame(LOST, loss.getBuilder());
        assertNull(lostBuilders.takeOver(plan));
    }

    @Test
    void aDispatchOfAPlanThatLostNoBuilderPairsWithNothing() {
        LostBuilders lostBuilders = new LostBuilders();
        lostBuilders.lost(den(), BuilderLossReason.ROLE_CHANGED, LOST);

        assertNull(lostBuilders.takeOver(den()));
    }

    @Test
    void aSecondLossBeforeARedispatchReplacesTheFirst() {
        LostBuilders lostBuilders = new LostBuilders();
        Plan plan = den();
        lostBuilders.lost(plan, BuilderLossReason.ROLE_CHANGED, LOST);
        lostBuilders.lost(plan, BuilderLossReason.STRAYED, WALKING);

        LostBuilders.Loss loss = lostBuilders.takeOver(plan);

        assertSame(BuilderLossReason.STRAYED, loss.getReason());
        assertSame(WALKING, loss.getBuilder());
    }

    @Test
    void aPlanCancelledOrCompletedBeforeARedispatchIsForgotten() {
        LostBuilders lostBuilders = new LostBuilders();
        Plan cancelled = den();
        Plan completed = den();
        Plan waiting = den();
        Plan requeued = den();
        lostBuilders.lost(cancelled, BuilderLossReason.STRAYED, LOST);
        lostBuilders.lost(completed, BuilderLossReason.STRAYED, LOST);
        lostBuilders.lost(waiting, BuilderLossReason.STRAYED, LOST);
        lostBuilders.lost(requeued, BuilderLossReason.STRAYED, LOST);
        cancelled.setState(PlanState.CANCELLED);
        completed.setState(PlanState.COMPLETE);
        requeued.setState(PlanState.PLANNED);

        lostBuilders.forgetSettled();

        assertEquals(2, lostBuilders.size());
        assertSame(LOST, lostBuilders.takeOver(waiting).getBuilder());
        assertSame(LOST, lostBuilders.takeOver(requeued).getBuilder());
    }

    @Test
    void aDispatchedBuilderKeepsItsLastReading() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        dispatched.dispatch("drone161", den());
        dispatched.read("drone161", WALKING);
        dispatched.read("drone161", LOST);

        assertSame(LOST, dispatched.lastReadingOf("drone161"));
    }

    @Test
    void aBuilderNotDispatchedIsNotRead() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();

        dispatched.read("drone161", WALKING);

        assertNull(dispatched.lastReadingOf("drone161"));
    }

    @Test
    void dispatchingAgainDropsTheLastReading() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        dispatched.dispatch("drone161", den());
        dispatched.read("drone161", WALKING);

        dispatched.dispatch("drone161", den());

        assertNull(dispatched.lastReadingOf("drone161"));
    }

    @Test
    void undispatchingDropsTheLastReading() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        dispatched.dispatch("drone161", den());
        dispatched.read("drone161", WALKING);

        dispatched.undispatch("drone161");

        assertNull(dispatched.lastReadingOf("drone161"));
    }

    @Test
    void aBuilderGoneWhileItsPlanIsCancelledDiedOnItsWalk() {
        assertTrue(PlanManager.diedOnItsWalk(PlanState.CANCELLED, false));
    }

    @Test
    void aBuilderStillAliveWhenItsPlanIsCancelledDidNotDie() {
        assertFalse(PlanManager.diedOnItsWalk(PlanState.CANCELLED, true));
    }

    @Test
    void aDroneThatBecameAnExtractorCompletedItsPlanRatherThanDying() {
        assertFalse(PlanManager.diedOnItsWalk(PlanState.COMPLETE, false));
    }

    @Test
    void aBuilderWhosePlanMorphsOrWasRequeuedDidNotDie() {
        assertFalse(PlanManager.diedOnItsWalk(PlanState.MORPHING, true));
        assertFalse(PlanManager.diedOnItsWalk(PlanState.PLANNED, true));
    }
}
