package macro.plan;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;
import unit.managed.BuilderStall;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderLossTrackingTest {

    private static final BuilderReading LOST =
            new BuilderReading(161, UnitRole.GATHER, "MoveToMinerals", 914, false);
    private static final BuilderReading WALKING = new BuilderReading(161, UnitRole.BUILD, "Move", 480, false);
    private static final BuilderReading TAKER = new BuilderReading(204, UnitRole.BUILD, "Move", 176, false);

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

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
        dispatched.dispatch("drone161", den(), 0, 0);
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
        dispatched.dispatch("drone161", den(), 0, 0);
        dispatched.read("drone161", WALKING);

        dispatched.dispatch("drone161", den(), 0, 0);

        assertNull(dispatched.lastReadingOf("drone161"));
    }

    @Test
    void undispatchingDropsTheLastReading() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        dispatched.dispatch("drone161", den(), 0, 0);
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

    @Test
    void aLossIsReportedWithTheLostBuilderAndPairsWithTheNextDispatch() {
        List<String> events = record();
        LostBuilders lostBuilders = new LostBuilders();
        Plan plan = den();

        PlanManager.reportLoss(lostBuilders, plan, BuilderLossReason.STRAYED, LOST);
        PlanManager.reportRedispatch(lostBuilders, plan, TAKER);

        assertEquals(Arrays.asList("LOST:STRAYED:161", "REDISPATCH:STRAYED:161>204"), events);
    }

    @Test
    void aSecondDispatchAfterARedispatchIsNotReportedAgain() {
        List<String> events = record();
        LostBuilders lostBuilders = new LostBuilders();
        Plan plan = den();
        PlanManager.reportLoss(lostBuilders, plan, BuilderLossReason.ROLE_CHANGED, LOST);
        PlanManager.reportRedispatch(lostBuilders, plan, TAKER);

        PlanManager.reportRedispatch(lostBuilders, plan, WALKING);

        assertEquals(Arrays.asList("LOST:ROLE_CHANGED:161", "REDISPATCH:ROLE_CHANGED:161>204"), events);
    }

    @Test
    void aDispatchOfAPlanThatLostNoBuilderReportsNothing() {
        List<String> events = record();

        PlanManager.reportRedispatch(new LostBuilders(), den(), TAKER);

        assertTrue(events.isEmpty());
    }

    @Test
    void aBuilderThatDiedIsReportedButNeverPaired() {
        List<String> events = record();
        LostBuilders lostBuilders = new LostBuilders();
        Plan plan = den();

        PlanManager.reportLoss(lostBuilders, plan, BuilderLossReason.DIED, LOST);
        PlanManager.reportRedispatch(lostBuilders, plan, TAKER);

        assertEquals(Collections.singletonList("LOST:DIED:161"), events);
        assertEquals(0, lostBuilders.size());
    }

    @Test
    void aStrayReleasedAfterTheGracePeriodIsReportedOnceAndRedispatchedToAnotherDrone() {
        List<String> events = record();
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        BuilderReleases<String> releases = new BuilderReleases<>();
        LostBuilders lostBuilders = new LostBuilders();
        Plan plan = den();
        plan.setState(PlanState.BUILDING);
        int dispatchFrame = 5000;
        int travelFrames = 400;
        int graceEnd = dispatchFrame + travelFrames * BuilderStray.GRACE_MARGIN_PERCENT / 100;
        dispatched.dispatch("drone161", plan, dispatchFrame, travelFrames);
        Position standing = new Position(1000, 1000);
        int far = BuilderStray.STRAY_DISTANCE + 200;

        int releaseFrame = -1;
        for (int frame = dispatchFrame; frame <= graceEnd + BuilderStray.STUCK_FRAMES && releaseFrame < 0; frame++) {
            int distance = frame < graceEnd ? far + (frame - dispatchFrame) : far + 1000 + (frame - graceEnd);
            boolean strayed = dispatched.strayOf("drone161").isStrayed(standing, distance, false, true, false, frame);
            BuilderLossReason reason = BuilderLossReason.of(true, true, strayed);
            if (reason != null) {
                releaseFrame = frame;
                dispatched.undispatch("drone161");
                releases.record(plan, "drone161", reason, frame);
                PlanManager.reportLoss(lostBuilders, plan, reason, LOST);
            }
        }

        assertEquals(graceEnd + BuilderStray.STRAY_FRAMES, releaseFrame);
        int redispatchFrame = releaseFrame + 1;
        List<String> candidates = Arrays.asList("drone161", "drone204").stream()
                .filter(d -> !releases.isBackedOff(plan, d, redispatchFrame))
                .collect(Collectors.toList());
        assertEquals(Collections.singletonList("drone204"), candidates);
        dispatched.dispatch(candidates.get(0), plan, redispatchFrame, travelFrames);
        PlanManager.reportRedispatch(lostBuilders, plan, TAKER);
        PlanManager.reportRedispatch(lostBuilders, plan, TAKER);

        assertEquals(Arrays.asList("LOST:STRAYED:161", "REDISPATCH:STRAYED:161>204"), events);
    }

    private static List<String> record() {
        List<String> events = new ArrayList<>();
        PlanEvents.register(new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
            }

            @Override
            public void onBuilderLost(Plan plan, BuilderLossReason reason, BuilderReading builder) {
                events.add("LOST:" + reason + ":" + builder.getUnitId());
            }

            @Override
            public void onBuilderRedispatch(Plan plan, BuilderLossReason reason, BuilderReading lost,
                                            BuilderReading taker) {
                events.add("REDISPATCH:" + reason + ":" + lost.getUnitId() + ">" + taker.getUnitId());
            }
        });
        return events;
    }
}
