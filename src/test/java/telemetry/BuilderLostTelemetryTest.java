package telemetry;

import bwapi.TilePosition;
import bwapi.UnitType;
import macro.plan.BuilderDispatchDecision;
import macro.plan.BuilderLossReason;
import macro.plan.BuilderReading;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderLostTelemetryTest {

    private static final BuilderReading DRONE_161 =
            new BuilderReading(161, UnitRole.GATHER, "MoveToMinerals", 914, false);
    private static final BuilderReading DRONE_204 =
            new BuilderReading(204, UnitRole.BUILD, "Move", 176, false);

    private final List<String> events = new ArrayList<>();

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    private static Plan den() {
        return new BuildingPlan(UnitType.Zerg_Hydralisk_Den, 1, new TilePosition(40, 20));
    }

    @Test
    void theBuilderColumnsAreAppendedAfterTheEnemyMainColumnsInOneBlock() {
        List<String> columns = Arrays.asList(PlanEventLogger.PLAN_HEADER.split(",", -1));
        int role = columns.indexOf("builder_role");
        assertEquals(columns.indexOf("enemy_main_source_y") + 1, role);
        assertEquals(role + 1, columns.indexOf("builder_order"));
        assertEquals(role + 2, columns.indexOf("builder_in_range"));
        assertEquals(role + 3, columns.indexOf("previous_executor_unit_id"));
    }

    @Test
    void everyRowShapeWritesOneCellPerBuilderColumn() {
        int builderColumns = 4;
        assertEquals(builderColumns, BuilderColumns.BLANK.trailing().size());
        assertEquals(builderColumns, BuilderColumns.current(null).trailing().size());
        assertEquals(builderColumns, BuilderColumns.lost(BuilderLossReason.DIED, DRONE_161).trailing().size());
        assertEquals(builderColumns,
                BuilderColumns.redispatch(BuilderLossReason.STRAYED, DRONE_161, DRONE_204).trailing().size());
    }

    @Test
    void aLostRowCarriesTheLostBuilderItsReasonDistanceAndRole() {
        BuilderColumns columns = BuilderColumns.lost(BuilderLossReason.ROLE_CHANGED, DRONE_161);

        assertEquals("161", columns.executorUnitId());
        assertEquals("914", columns.distance());
        assertSame(BuilderDispatchDecision.LOST_ROLE_CHANGED, columns.decision());
        assertEquals(Arrays.asList("GATHER", "MoveToMinerals", "false", ""), columns.trailing());
    }

    @Test
    void aRedispatchRowCarriesTheNewBuilderAndTheOldOneAndTheReason() {
        BuilderColumns columns = BuilderColumns.redispatch(BuilderLossReason.STRAYED, DRONE_161, DRONE_204);

        assertEquals("204", columns.executorUnitId());
        assertEquals("176", columns.distance());
        assertSame(BuilderDispatchDecision.LOST_STRAYED, columns.decision());
        assertEquals(Arrays.asList("BUILD", "Move", "false", "161"), columns.trailing());
    }

    @Test
    void aBuilderKilledOnItsWalkIsReportedAsDied() {
        BuilderColumns columns = BuilderColumns.lost(BuilderLossReason.DIED, DRONE_161);

        assertSame(BuilderDispatchDecision.LOST_DIED, columns.decision());
        assertEquals("161", columns.executorUnitId());
    }

    @Test
    void everyLossReasonIsReportedUnderItsOwnDecision() {
        for (BuilderLossReason reason : BuilderLossReason.values()) {
            BuilderDispatchDecision decision = BuilderColumns.lost(reason, DRONE_161).decision();
            assertTrue(decision.toString().endsWith(reason.toString()));
        }
    }

    @Test
    void aHoldRowForAPlanWithNoExecutorSaysSoRatherThanLeavingTheRoleBlank() {
        BuilderColumns columns = BuilderColumns.current(null);

        assertEquals("", columns.executorUnitId());
        assertEquals("", columns.distance());
        assertEquals(Arrays.asList(BuilderColumns.NO_EXECUTOR, "", "", ""), columns.trailing());
    }

    @Test
    void anExecutorNoManagedUnitWrapsReadsUnmanaged() {
        BuilderReading hatchery = new BuilderReading(7, null, "Nothing", 0, null);

        assertEquals(Arrays.asList(BuilderColumns.UNMANAGED, "Nothing", "", ""),
                BuilderColumns.current(hatchery).trailing());
    }

    @Test
    void aBuilderWithoutABuildTileHasNoDistance() {
        BuilderReading drone = new BuilderReading(9, UnitRole.BUILD, "Move", null, null);

        assertEquals("", BuilderColumns.current(drone).distance());
    }

    @Test
    void aDispatchDecisionRowCarriesTheDecisionAndTheExecutorsRole() {
        BuilderColumns columns = BuilderColumns.gateDecision(DRONE_204, BuilderDispatchDecision.RECALLED);

        assertSame(BuilderDispatchDecision.RECALLED, columns.decision());
        assertEquals("204", columns.executorUnitId());
        assertEquals("BUILD", columns.trailing().get(0));
    }

    @Test
    void rowShapesThatDoNotReportTheBuilderLeaveEveryBuilderColumnBlank() {
        assertNull(BuilderColumns.BLANK.decision());
        assertEquals(Arrays.asList("", "", "", ""), BuilderColumns.BLANK.trailing());
    }

    @Test
    void lossAndRedispatchReachTheRegisteredSink() {
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
        Plan plan = den();

        PlanEvents.builderLost(plan, BuilderLossReason.STRAYED, DRONE_161);
        PlanEvents.builderRedispatched(plan, BuilderLossReason.STRAYED, DRONE_161, DRONE_204);

        assertEquals(Arrays.asList("LOST:STRAYED:161", "REDISPATCH:STRAYED:161>204"), events);
    }

    @Test
    void lossAndRedispatchAreSilentWithNoSinkRegistered() {
        PlanEvents.builderLost(den(), BuilderLossReason.DIED, DRONE_161);
        PlanEvents.builderRedispatched(den(), BuilderLossReason.DIED, DRONE_161, DRONE_204);

        assertTrue(events.isEmpty());
    }
}
