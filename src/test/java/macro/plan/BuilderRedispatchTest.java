package macro.plan;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.ResourceCount;
import macro.BuildAheadSlot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderRedispatchTest {

    private static final UnitType DEN = UnitType.Zerg_Hydralisk_Den;
    private static final TilePosition DEN_TILE = new TilePosition(40, 20);
    private static final int CLAIM_FRAME = 5001;
    private static final int FAR = BuilderStray.STRAY_DISTANCE + 100;

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

        assertSame(BuilderLossReason.ROLE_CHANGED, BuilderLossReason.of(false, true, false));
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
    void anAffordableBuilderWalkingAwayIsStrayedAfterTheStrayWindow() {
        BuilderStray stray = new BuilderStray();
        int frame = CLAIM_FRAME;
        assertFalse(stray.isStrayed(FAR, true, false, frame));
        int receded = FAR + BuilderStray.CLOSING_PROGRESS + 1;
        assertFalse(stray.isStrayed(receded, true, false, frame + BuilderStray.STRAY_FRAMES - 1));
        assertTrue(stray.isStrayed(receded, true, false, frame + BuilderStray.STRAY_FRAMES));
    }

    @Test
    void aBuilderStandingStillBeyondTheStrayDistanceIsStrayedAfterTheStuckWindow() {
        BuilderStray stray = new BuilderStray();
        int frame = CLAIM_FRAME;
        assertFalse(stray.isStrayed(FAR, true, false, frame));
        assertFalse(stray.isStrayed(FAR, true, false, frame + BuilderStray.STRAY_FRAMES));
        assertFalse(stray.isStrayed(FAR, true, false, frame + BuilderStray.STUCK_FRAMES - 1));
        assertTrue(stray.isStrayed(FAR, true, false, frame + BuilderStray.STUCK_FRAMES));
    }

    @Test
    void aBuilderClosingOnTheSiteIsNeverStrayed() {
        BuilderStray stray = new BuilderStray();
        double distance = 1200;
        for (int frame = CLAIM_FRAME; frame < CLAIM_FRAME + 2 * BuilderStray.STUCK_FRAMES; frame++) {
            assertFalse(stray.isStrayed(distance, true, false, frame));
            distance = Math.max(0, distance - 4);
        }
    }

    @Test
    void aBuilderDetouringBrieflyAndThenClosingIsNotStrayed() {
        BuilderStray stray = new BuilderStray();
        int frame = CLAIM_FRAME;
        assertFalse(stray.isStrayed(FAR, true, false, frame));
        assertFalse(stray.isStrayed(FAR + 100, true, false, frame + BuilderStray.STRAY_FRAMES - 1));
        assertFalse(stray.isStrayed(FAR - BuilderStray.CLOSING_PROGRESS, true, false,
                frame + BuilderStray.STRAY_FRAMES));
        assertFalse(stray.isStrayed(FAR + 100, true, false, frame + 2 * BuilderStray.STRAY_FRAMES - 1));
    }

    @Test
    void aBuilderIsNeverStrayedWhileThePlanIsUnaffordable() {
        BuilderStray stray = new BuilderStray();
        for (int frame = CLAIM_FRAME; frame <= CLAIM_FRAME + 2 * BuilderStray.STUCK_FRAMES; frame++) {
            assertFalse(stray.isStrayed(FAR * 3, false, false, frame));
        }
    }

    @Test
    void aBuilderReturningCargoOrClearingABlockerIsNeverStrayed() {
        BuilderStray stray = new BuilderStray();
        for (int frame = CLAIM_FRAME; frame <= CLAIM_FRAME + 2 * BuilderStray.STUCK_FRAMES; frame++) {
            assertFalse(stray.isStrayed(FAR * 3, true, true, frame));
        }
    }

    @Test
    void aBuilderWithinTheStrayDistanceIsNeverStrayed() {
        BuilderStray stray = new BuilderStray();
        for (int frame = CLAIM_FRAME; frame <= CLAIM_FRAME + 2 * BuilderStray.STUCK_FRAMES; frame++) {
            assertFalse(stray.isStrayed(BuilderStray.STRAY_DISTANCE, true, false, frame));
        }
    }

    @Test
    void theStrayClockRestartsWhenThePlanBecomesAffordable() {
        BuilderStray stray = new BuilderStray();
        assertFalse(stray.isStrayed(FAR, true, false, CLAIM_FRAME));
        assertFalse(stray.isStrayed(FAR, false, false, CLAIM_FRAME + 1));
        int resumed = CLAIM_FRAME + 2;
        assertFalse(stray.isStrayed(FAR, true, false, resumed));
        assertFalse(stray.isStrayed(FAR, true, false, resumed + BuilderStray.STUCK_FRAMES - 1));
        assertTrue(stray.isStrayed(FAR, true, false, resumed + BuilderStray.STUCK_FRAMES));
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
}
