package macro.plan;

import bwapi.Order;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.ResourceCount;
import macro.BuildAheadSlot;
import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderRedispatchTest {

    private static final UnitType DEN = UnitType.Zerg_Hydralisk_Den;
    private static final TilePosition DEN_TILE = new TilePosition(40, 20);
    private static final int CLAIM_FRAME = 5001;
    private static final int FAR = BuilderStray.STRAY_DISTANCE + 100;
    private static final Position HERE = new Position(640, 640);

    private static BuilderStray noGrace() {
        return new BuilderStray(CLAIM_FRAME, 0);
    }

    private static Plan buildingDen() {
        Plan plan = new BuildingPlan(DEN, 1, DEN_TILE);
        plan.setState(PlanState.BUILDING);
        return plan;
    }

    @Test
    void aBuilderWhoseRoleChangedIsLost() {
        assertSame(BuilderLossReason.ROLE_CHANGED, BuilderLossReason.of(false, true, false));
        assertSame(BuilderLossReason.ROLE_CHANGED, BuilderLossReason.of(false, false, true));
    }

    @Test
    void aBuilderNoLongerBoundToThePlanIsLost() {
        assertSame(BuilderLossReason.PLAN_UNBOUND, BuilderLossReason.of(true, false, false));
    }

    @Test
    void aStrayedBuilderIsLost() {
        assertSame(BuilderLossReason.STRAYED, BuilderLossReason.of(true, true, true));
    }

    @Test
    void aBoundBuilderOnItsWayIsKept() {
        assertNull(BuilderLossReason.of(true, true, false));
    }

    @Test
    void everyLossIsReportedAsANonDispatchDecision() {
        for (BuilderLossReason reason : BuilderLossReason.values()) {
            assertFalse(reason.decision().isDispatch());
        }
    }

    @Test
    void aPlanWhoseBuilderChangedRoleReturnsToScheduleWithItsClaimAndReservation() {
        Plan plan = buildingDen();
        Map<String, Plan> assigned = new HashMap<>();
        assigned.put("drone161", plan);
        Set<Plan> building = new HashSet<>();
        building.add(plan);
        Set<Plan> scheduled = new HashSet<>();
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(plan, CLAIM_FRAME, CLAIM_FRAME + 400);
        ResourceCount resourceCount = new ResourceCount(null);
        resourceCount.reserveUnit(DEN);

        PlanManager.returnToSchedule(plan, assigned, building, scheduled);

        assertSame(PlanState.SCHEDULE, plan.getState());
        assertTrue(scheduled.contains(plan));
        assertFalse(building.contains(plan));
        assertFalse(assigned.containsKey("drone161"));
        Set<Plan> active = new HashSet<>(scheduled);
        active.addAll(building);
        slot.reconcile(active);
        assertTrue(slot.claimedPlans().contains(plan));
        assertEquals(DEN.mineralPrice(), resourceCount.getReservedMinerals());
        assertEquals(DEN.gasPrice(), resourceCount.getReservedGas());
    }

    @Test
    void aReturnedPlanLeavesNoDuplicateExecutorOrPlanMapping() {
        Plan plan = buildingDen();
        Plan other = buildingDen();
        Map<String, Plan> assigned = new HashMap<>();
        assigned.put("drone161", plan);
        assigned.put("drone170", plan);
        assigned.put("drone180", other);
        Set<Plan> building = new HashSet<>();
        building.add(plan);
        building.add(other);
        Set<Plan> scheduled = new HashSet<>();

        PlanManager.returnToSchedule(plan, assigned, building, scheduled);
        assigned.put("drone216", plan);

        assertEquals(1, assigned.values().stream().filter(plan::equals).count());
        assertSame(plan, assigned.get("drone216"));
        assertFalse(assigned.containsKey("drone161"));
        assertFalse(assigned.containsKey("drone170"));
        assertSame(other, assigned.get("drone180"));
        assertTrue(building.contains(other));
        assertSame(PlanState.BUILDING, other.getState());
    }

    @Test
    void anAffordableBuilderWalkingAwayOnAnotherOrderIsStrayedAfterTheStrayWindow() {
        BuilderStray stray = noGrace();
        int frame = CLAIM_FRAME;
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, frame));
        int receded = FAR + BuilderStray.CLOSING_PROGRESS + 1;
        assertFalse(stray.isStrayed(HERE, receded, false, true, false, frame + BuilderStray.STRAY_FRAMES - 1));
        assertTrue(stray.isStrayed(HERE, receded, false, true, false, frame + BuilderStray.STRAY_FRAMES));
    }

    @Test
    void aBuilderStandingStillBeyondTheStrayDistanceIsStrayedAfterTheStuckWindow() {
        BuilderStray stray = noGrace();
        int frame = CLAIM_FRAME;
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, frame));
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, frame + BuilderStray.STRAY_FRAMES));
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, frame + BuilderStray.STUCK_FRAMES - 1));
        assertTrue(stray.isStrayed(HERE, FAR, false, true, false, frame + BuilderStray.STUCK_FRAMES));
    }

    @Test
    void aBuilderHeadingToTheSiteButStandingStillIsStrayedAfterTheStuckWindow() {
        BuilderStray stray = noGrace();
        int frame = CLAIM_FRAME;
        assertFalse(stray.isStrayed(HERE, FAR, true, true, false, frame));
        assertFalse(stray.isStrayed(HERE, FAR, true, true, false, frame + BuilderStray.STUCK_FRAMES - 1));
        assertTrue(stray.isStrayed(HERE, FAR, true, true, false, frame + BuilderStray.STUCK_FRAMES));
    }

    @Test
    void aBuilderClosingOnTheSiteIsNeverStrayed() {
        BuilderStray stray = noGrace();
        double distance = 1200;
        for (int frame = CLAIM_FRAME; frame < CLAIM_FRAME + 2 * BuilderStray.STUCK_FRAMES; frame++) {
            assertFalse(stray.isStrayed(HERE, distance, false, true, false, frame));
            distance = Math.max(0, distance - 4);
        }
    }

    @Test
    void aBuilderAdvancingAlongARouteThatRecedesInAStraightLineIsNeverStrayed() {
        for (int recedingFrames = 60; recedingFrames <= 200; recedingFrames += 20) {
            BuilderStray stray = noGrace();
            double distance = FAR;
            int x = HERE.getX();
            int frame = CLAIM_FRAME;
            for (; frame < CLAIM_FRAME + recedingFrames; frame++) {
                assertFalse(stray.isStrayed(new Position(x, HERE.getY()), distance, true, true, false, frame));
                x += 4;
                distance += 2;
            }
            for (; frame < CLAIM_FRAME + recedingFrames + 2 * BuilderStray.STUCK_FRAMES; frame++) {
                assertFalse(stray.isStrayed(new Position(x, HERE.getY()), distance, true, true, false, frame));
                x += 4;
                distance = Math.max(0, distance - 3);
            }
        }
    }

    @Test
    void aBuilderIsNeverStrayedWithinTheGracePeriodAfterDispatch() {
        int travelFrames = 400;
        int graceEnd = CLAIM_FRAME + travelFrames * BuilderStray.GRACE_MARGIN_PERCENT / 100;
        BuilderStray stray = new BuilderStray(CLAIM_FRAME, travelFrames);
        int receded = FAR + 10 * BuilderStray.CLOSING_PROGRESS;
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, CLAIM_FRAME));
        for (int frame = CLAIM_FRAME + 1; frame < graceEnd; frame++) {
            assertFalse(stray.isStrayed(HERE, receded, false, true, false, frame));
        }
        assertFalse(stray.isStrayed(HERE, receded, false, true, false, graceEnd));
        assertFalse(stray.isStrayed(HERE, receded, false, true, false, graceEnd + BuilderStray.STUCK_FRAMES - 1));
        assertTrue(stray.isStrayed(HERE, receded, false, true, false, graceEnd + BuilderStray.STUCK_FRAMES));
    }

    @Test
    void theGracePeriodIsTheTravelEstimateTimesTheMargin() {
        assertTrue(BuilderStray.GRACE_MARGIN_PERCENT > 100);
        int travelFrames = 300;
        BuilderStray stray = new BuilderStray(CLAIM_FRAME, travelFrames);
        int graceEnd = CLAIM_FRAME + travelFrames * BuilderStray.GRACE_MARGIN_PERCENT / 100;
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, graceEnd - 1));
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, graceEnd));
        assertTrue(stray.isStrayed(HERE, FAR, false, true, false, graceEnd + BuilderStray.STUCK_FRAMES));
    }

    @Test
    void aBuilderDetouringBrieflyAndThenClosingIsNotStrayed() {
        BuilderStray stray = noGrace();
        int frame = CLAIM_FRAME;
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, frame));
        assertFalse(stray.isStrayed(HERE, FAR + 100, false, true, false, frame + BuilderStray.STRAY_FRAMES - 1));
        assertFalse(stray.isStrayed(HERE, FAR - BuilderStray.CLOSING_PROGRESS, false, true, false,
                frame + BuilderStray.STRAY_FRAMES));
        assertFalse(stray.isStrayed(HERE, FAR + 100, false, true, false, frame + 2 * BuilderStray.STRAY_FRAMES - 1));
    }

    @Test
    void aBuilderIsNeverStrayedWhileThePlanIsUnaffordable() {
        BuilderStray stray = noGrace();
        for (int frame = CLAIM_FRAME; frame <= CLAIM_FRAME + 2 * BuilderStray.STUCK_FRAMES; frame++) {
            assertFalse(stray.isStrayed(HERE, FAR * 3, false, false, false, frame));
        }
    }

    @Test
    void aBuilderReturningCargoOrClearingABlockerIsNeverStrayed() {
        BuilderStray stray = noGrace();
        for (int frame = CLAIM_FRAME; frame <= CLAIM_FRAME + 2 * BuilderStray.STUCK_FRAMES; frame++) {
            assertFalse(stray.isStrayed(HERE, FAR * 3, false, true, true, frame));
        }
    }

    @Test
    void aBuilderWithinTheStrayDistanceIsNeverStrayed() {
        BuilderStray stray = noGrace();
        for (int frame = CLAIM_FRAME; frame <= CLAIM_FRAME + 2 * BuilderStray.STUCK_FRAMES; frame++) {
            assertFalse(stray.isStrayed(HERE, BuilderStray.STRAY_DISTANCE, false, true, false, frame));
        }
    }

    @Test
    void theStrayClockRestartsWhenThePlanBecomesAffordable() {
        BuilderStray stray = noGrace();
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, CLAIM_FRAME));
        assertFalse(stray.isStrayed(HERE, FAR, false, false, false, CLAIM_FRAME + 1));
        int resumed = CLAIM_FRAME + 2;
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, resumed));
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, resumed + BuilderStray.STUCK_FRAMES - 1));
        assertTrue(stray.isStrayed(HERE, FAR, false, true, false, resumed + BuilderStray.STUCK_FRAMES));
    }

    @Test
    void aBuilderIsHeadingToTheSiteOnlyOnAMoveOrderToItsMoveTarget() {
        Position target = PlanManager.siteMoveTarget(DEN, DEN_TILE);
        Position nearTarget = new Position(target.getX() + BuilderStray.CLOSING_PROGRESS, target.getY());
        Position elsewhere = new Position(target.getX() + BuilderStray.CLOSING_PROGRESS + 1, target.getY());
        assertTrue(PlanManager.isHeadingTo(Order.Move, target, target));
        assertTrue(PlanManager.isHeadingTo(Order.Move, nearTarget, target));
        assertFalse(PlanManager.isHeadingTo(Order.Move, elsewhere, target));
        assertFalse(PlanManager.isHeadingTo(Order.MoveToMinerals, target, target));
        assertFalse(PlanManager.isHeadingTo(Order.PlayerGuard, target, target));
        assertFalse(PlanManager.isHeadingTo(Order.Move, null, target));
    }

    @Test
    void theJustReleasedBuilderIsNotRepickedForThePlanWithinTheBackoff() {
        BuilderReleases<String> releases = new BuilderReleases<>();
        Plan plan = buildingDen();
        Plan other = buildingDen();
        releases.record(plan, "drone425", BuilderLossReason.STRAYED, CLAIM_FRAME);

        assertTrue(releases.isBackedOff(plan, "drone425", CLAIM_FRAME + 1));
        assertTrue(releases.isBackedOff(plan, "drone425",
                CLAIM_FRAME + BuilderReleases.RESELECT_BACKOFF_FRAMES - 1));
        assertFalse(releases.isBackedOff(plan, "drone425", CLAIM_FRAME + BuilderReleases.RESELECT_BACKOFF_FRAMES));
        assertFalse(releases.isBackedOff(plan, "drone459", CLAIM_FRAME + 1));
        assertFalse(releases.isBackedOff(other, "drone425", CLAIM_FRAME + 1));
    }

    @Test
    void bothDronesOfAPingPongAreBackedOff() {
        BuilderReleases<String> releases = new BuilderReleases<>();
        Plan plan = buildingDen();
        releases.record(plan, "drone425", BuilderLossReason.STRAYED, CLAIM_FRAME);
        releases.record(plan, "drone459", BuilderLossReason.ROLE_CHANGED, CLAIM_FRAME + 50);

        assertTrue(releases.isBackedOff(plan, "drone425", CLAIM_FRAME + 51));
        assertTrue(releases.isBackedOff(plan, "drone459", CLAIM_FRAME + 51));
    }

    @Test
    void aPlanStopsStrayingItsBuildersAtTheCap() {
        BuilderReleases<String> releases = new BuilderReleases<>();
        Plan plan = buildingDen();
        for (int stray = 0; stray < BuilderReleases.MAX_STRAYS_PER_PLAN; stray++) {
            assertTrue(releases.mayStray(plan));
            releases.record(plan, "drone" + stray, BuilderLossReason.STRAYED, CLAIM_FRAME + stray);
        }
        assertFalse(releases.mayStray(plan));
        assertTrue(releases.mayStray(buildingDen()));
    }

    @Test
    void onlyStraysCountTowardTheCap() {
        BuilderReleases<String> releases = new BuilderReleases<>();
        Plan plan = buildingDen();
        for (int release = 0; release < 2 * BuilderReleases.MAX_STRAYS_PER_PLAN; release++) {
            releases.record(plan, "drone" + release, BuilderLossReason.ROLE_CHANGED, CLAIM_FRAME);
            releases.record(plan, "drone" + release, BuilderLossReason.PLAN_UNBOUND, CLAIM_FRAME);
        }
        assertTrue(releases.mayStray(plan));
    }

    @Test
    void aForgottenPlanStartsAFreshLedger() {
        BuilderReleases<String> releases = new BuilderReleases<>();
        Plan plan = buildingDen();
        for (int stray = 0; stray < BuilderReleases.MAX_STRAYS_PER_PLAN; stray++) {
            releases.record(plan, "drone425", BuilderLossReason.STRAYED, CLAIM_FRAME);
        }
        releases.forget(plan);

        assertTrue(releases.mayStray(plan));
        assertFalse(releases.isBackedOff(plan, "drone425", CLAIM_FRAME + 1));
    }

    @Test
    void onlyAPlanThatWillNotBeDispatchedAgainIsSettled() {
        assertTrue(PlanManager.isSettled(PlanState.MORPHING));
        assertTrue(PlanManager.isSettled(PlanState.COMPLETE));
        assertTrue(PlanManager.isSettled(PlanState.CANCELLED));
        assertFalse(PlanManager.isSettled(PlanState.PLANNED));
        assertFalse(PlanManager.isSettled(PlanState.SCHEDULE));
        assertFalse(PlanManager.isSettled(PlanState.BUILDING));
    }

    @Test
    void theBankCoversAPlanOnlyWhenBothResourcesCoverItsOwnPrice() {
        Plan plan = buildingDen();
        assertTrue(PlanManager.coversCost(DEN.mineralPrice(), DEN.gasPrice(), plan));
        assertFalse(PlanManager.coversCost(DEN.mineralPrice() - 1, DEN.gasPrice(), plan));
        assertFalse(PlanManager.coversCost(DEN.mineralPrice(), DEN.gasPrice() - 1, plan));
    }

    @Test
    void theSiteMoveTargetIsTheCentreOfTheFootprint() {
        Position target = PlanManager.siteMoveTarget(DEN, DEN_TILE);
        assertEquals(DEN_TILE.toPosition().getX() + DEN.tileWidth() * 16, target.getX());
        assertEquals(DEN_TILE.toPosition().getY() + DEN.tileHeight() * 16, target.getY());
    }

    @Test
    void aReleasedBuilderStillInBuildWithNoPlanGoesIdle() {
        assertSame(UnitRole.IDLE, PlanManager.roleAfterRelease(UnitRole.BUILD, false));
    }

    @Test
    void aReleasedBuilderAnotherManagerReRoledKeepsItsRole() {
        assertSame(UnitRole.GATHER, PlanManager.roleAfterRelease(UnitRole.GATHER, false));
        assertSame(UnitRole.FIGHT, PlanManager.roleAfterRelease(UnitRole.FIGHT, false));
        assertSame(UnitRole.DEFEND, PlanManager.roleAfterRelease(UnitRole.DEFEND, true));
    }

    @Test
    void aReleasedBuilderInBuildThatStillHoldsAnotherPlanKeepsBuild() {
        assertSame(UnitRole.BUILD, PlanManager.roleAfterRelease(UnitRole.BUILD, true));
    }

    @Test
    void aDispatchedBuilderIsPairedWithItsPlanAndAStrayTracker() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        Plan plan = buildingDen();
        dispatched.dispatch("drone161", plan, CLAIM_FRAME, 0);

        assertSame(plan, dispatched.planOf("drone161"));
        assertNotNull(dispatched.strayOf("drone161"));
        assertEquals(1, dispatched.snapshot().size());
    }

    @Test
    void anUndispatchedBuilderLosesBothItsPlanAndItsStrayTracker() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        dispatched.dispatch("drone161", buildingDen(), CLAIM_FRAME, 0);
        dispatched.undispatch("drone161");

        assertNull(dispatched.planOf("drone161"));
        assertNull(dispatched.strayOf("drone161"));
        assertTrue(dispatched.snapshot().isEmpty());
    }

    @Test
    void aBuilderDispatchedAgainStartsAFreshStrayHistory() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        Plan first = buildingDen();
        Plan second = buildingDen();
        dispatched.dispatch("drone161", first, CLAIM_FRAME, 0);
        BuilderStray stray = dispatched.strayOf("drone161");
        assertFalse(stray.isStrayed(HERE, FAR, false, true, false, CLAIM_FRAME));

        dispatched.dispatch("drone161", second, CLAIM_FRAME, 0);

        assertSame(second, dispatched.planOf("drone161"));
        assertNotSame(stray, dispatched.strayOf("drone161"));
        assertEquals(1, dispatched.snapshot().size());
        assertFalse(dispatched.strayOf("drone161").isStrayed(HERE, FAR, false, true, false,
                CLAIM_FRAME + BuilderStray.STUCK_FRAMES));
    }

    @Test
    void theSnapshotSurvivesUndispatchingDuringIteration() {
        DispatchedBuilders<String> dispatched = new DispatchedBuilders<>();
        dispatched.dispatch("drone161", buildingDen(), CLAIM_FRAME, 0);
        dispatched.dispatch("drone170", buildingDen(), CLAIM_FRAME, 0);

        for (Map.Entry<String, Plan> entry : dispatched.snapshot()) {
            dispatched.undispatch(entry.getKey());
        }

        assertTrue(dispatched.snapshot().isEmpty());
    }
}
