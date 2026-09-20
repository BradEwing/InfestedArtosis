package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.squad.CombatSimulator;
import unit.squad.DefenseSim;
import unit.squad.GroundSquad;
import unit.squad.Squad;
import unit.squad.SquadStatus;
import util.Arc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadDecisionsTest {

    private final List<String> events = new ArrayList<>();

    private SquadDecisionSink recorder() {
        return new SquadDecisionSink() {
            @Override
            public void onSimEvaluated(Squad squad, CombatSimulator.CombatResult result, boolean retreatLocked,
                                       boolean fightLocked) {
                events.add("SIM:" + result + ":" + retreatLocked + ":" + fightLocked);
            }

            @Override
            public void onLockSuppressed(Squad squad, SquadLock lock) {
                events.add("LOCK:" + lock);
            }

            @Override
            public void onSplitSuppressed(Squad squad, int moveOutThreshold, int squadStrength,
                                          int outlierStrength) {
                events.add("SPLIT:" + squad.getId() + ":" + moveOutThreshold + ":" + squadStrength + ":"
                        + outlierStrength);
            }

            @Override
            public void onRallied(Squad squad, RallyReason reason) {
                events.add("RALLY:" + reason);
            }

            @Override
            public void onRallyReleased(Squad squad, RallyRelease release) {
                events.add("RELEASE:" + release);
            }

            @Override
            public void onContainmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment,
                                               boolean entered) {
                events.add("CONTAIN:" + shouldContain + ":" + canBreakContainment + ":" + entered);
            }

            @Override
            public void onDefenseEvaluated(Squad squad, DefenseEvent event, int candidates, int pulled, int released,
                                           DefenseSim sim) {
                events.add("DEFENSE:" + event + ":" + candidates + ":" + pulled + ":" + released);
            }
        };
    }

    private static String[] rowFor(Squad squad) {
        return rowFor(squad, "NONE");
    }

    private static String[] rowFor(Squad squad, String suppressedBy) {
        return rowFor(squad, suppressedBy, RallyReason.NONE, RallyRelease.NONE);
    }

    private static String[] rowFor(Squad squad, String suppressedBy, RallyReason reason, RallyRelease release) {
        SquadDecision context = new SquadDecision();
        String row = String.join(",", SquadDecisionLogger.identityCells("game-1", 1000, squad, "STATUS_CHANGE",
                SquadStatus.RETREAT, SquadStatus.FIGHT, context, suppressedBy))
                + "," + String.join(",", SquadDecisionLogger.squadCells(squad, context, false, -1))
                + "," + String.join(",", SquadDecisionLogger.rallyCells(reason, release))
                + "," + String.join(",", SquadDecisionLogger.defenseCells(-1, -1, -1, null))
                + "," + String.join(",", SquadDecisionLogger.arcCells(squad))
                + "," + String.join(",", SquadDecisionLogger.enemySampleCells(context));
        return row.split(",", -1);
    }

    private static int columnIndex(String column) {
        String[] columns = SquadDecisionLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    @AfterEach
    void clearSink() {
        SquadDecisions.clear();
    }

    @Test
    void dispatchIsANoOpWithoutASink() {
        Squad squad = new GroundSquad();

        SquadDecisions.simEvaluated(squad, CombatSimulator.CombatResult.ENGAGE, false, false);
        SquadDecisions.lockSuppressed(squad, SquadLock.FIGHT);
        SquadDecisions.containmentEvaluated(squad, true, false, true);
        SquadDecisions.splitSuppressed(squad, 8, 12, 2);

        assertTrue(events.isEmpty());
    }

    @Test
    void registeredSinkReceivesEveryDecisionInput() {
        SquadDecisions.register(recorder());
        Squad squad = new GroundSquad();

        SquadDecisions.simEvaluated(squad, CombatSimulator.CombatResult.RETREAT, true, false);
        SquadDecisions.lockSuppressed(squad, SquadLock.RETREAT);
        SquadDecisions.containmentEvaluated(squad, true, false, true);
        SquadDecisions.splitSuppressed(squad, 8, 12, 2);

        assertEquals(4, events.size());
        assertEquals("SIM:RETREAT:true:false", events.get(0));
        assertEquals("LOCK:RETREAT", events.get(1));
        assertEquals("CONTAIN:true:false:true", events.get(2));
        assertEquals("SPLIT:" + squad.getId() + ":8:12:2", events.get(3));
    }

    @Test
    void clearStopsDispatch() {
        SquadDecisions.register(recorder());
        SquadDecisions.clear();

        SquadDecisions.lockSuppressed(new GroundSquad(), SquadLock.FIGHT);

        assertTrue(events.isEmpty());
    }

    @Test
    void aLockOnlySuppressesWhenTheVerdictAsksForTheOtherStatus() {
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.RETREAT,
                CombatSimulator.CombatResult.ENGAGE));
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.RETREAT,
                CombatSimulator.CombatResult.ADVANCE));
        assertTrue(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT,
                CombatSimulator.CombatResult.RETREAT));

        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.RETREAT,
                CombatSimulator.CombatResult.RETREAT));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT,
                CombatSimulator.CombatResult.ENGAGE));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT,
                CombatSimulator.CombatResult.ADVANCE));
    }

    @Test
    void anUnevaluatedVerdictNeverCountsAsSuppression() {
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.RETREAT, SquadLock.RETREAT, null));
        assertFalse(SquadDecisionLogger.overridesVerdict(SquadStatus.FIGHT, SquadLock.FIGHT, null));
    }

    @Test
    void commitmentColumnsHoldFixedPositionsAfterTheLockColumns() {
        assertEquals(21, columnIndex("committed"));
        assertEquals(22, columnIndex("commit_frame"));

        assertEquals(columnIndex("fight_lock_until_frame") + 1, columnIndex("committed"));
        assertEquals(columnIndex("commit_frame") + 1, columnIndex("should_contain"));
    }

    @Test
    void everyRowCarriesExactlyTheHeaderColumnCount() {
        String[] fields = rowFor(new GroundSquad());

        assertEquals(SquadDecisionLogger.HEADER.split(",", -1).length, fields.length);
    }

    @Test
    void splitSuppressedRowsCarryTheMoveOutFloorInSuppressedBy() {
        String[] fields = rowFor(new GroundSquad(), "MOVE_OUT_FLOOR");

        assertEquals("MOVE_OUT_FLOOR", fields[columnIndex("suppressed_by")]);
    }

    @Test
    void committedSquadCarriesItsCommitFrame() {
        Squad squad = new GroundSquad();
        squad.commit(4321);

        String[] fields = rowFor(squad);

        assertEquals("1", fields[columnIndex("committed")]);
        assertEquals("4321", fields[columnIndex("commit_frame")]);
    }

    @Test
    void uncommittedSquadCarriesTheNotEvaluatedSentinel() {
        String[] fields = rowFor(new GroundSquad());

        assertEquals("0", fields[columnIndex("committed")]);
        assertEquals("-1", fields[columnIndex("commit_frame")]);
    }

    @Test
    void commitFrameIsNegativeOneExactlyWhenTheSquadIsNotCommitted() {
        Squad committed = new GroundSquad();
        committed.commit(500);

        Squad recalled = new GroundSquad();
        recalled.commit(500);
        recalled.clearCommitment();

        String[] committedFields = rowFor(committed);
        String[] recalledFields = rowFor(recalled);

        assertEquals("1", committedFields[columnIndex("committed")]);
        assertFalse("-1".equals(committedFields[columnIndex("commit_frame")]));
        assertEquals("0", recalledFields[columnIndex("committed")]);
        assertEquals("-1", recalledFields[columnIndex("commit_frame")]);
    }

    @Test
    void registeredSinkReceivesTheRallyEntryAndItsRelease() {
        SquadDecisions.register(recorder());
        Squad squad = new GroundSquad();

        SquadDecisions.rallied(squad, RallyReason.DEFILER_ONLY);
        SquadDecisions.rallyReleased(squad, RallyRelease.CLOSE_THREATS);

        assertEquals(2, events.size());
        assertEquals("RALLY:DEFILER_ONLY", events.get(0));
        assertEquals("RELEASE:CLOSE_THREATS", events.get(1));
    }

    @Test
    void rallyDispatchIsANoOpWithoutASink() {
        Squad squad = new GroundSquad();

        SquadDecisions.rallied(squad, RallyReason.STAGING);
        SquadDecisions.rallyReleased(squad, RallyRelease.MOVE_OUT_THRESHOLD);

        assertTrue(events.isEmpty());
    }

    @Test
    void everyRowCarriesTheRallyReasonAndRelease() {
        String[] fields = rowFor(new GroundSquad(), "NONE", RallyReason.DEFILER_ONLY, RallyRelease.DISBANDED);

        assertEquals("DEFILER_ONLY", fields[columnIndex("rally_reason")]);
        assertEquals("DISBANDED", fields[columnIndex("rally_release")]);
    }

    private static Arc computedArc() {
        Arc arc = new Arc(new Position(1600, 1600), new Position(1600, 960), 160, 90, 3);
        arc.compute(Collections.emptySet(), Collections.emptyList(), 0, 4096, 4096);
        return arc;
    }

    @Test
    void aContainingSquadCarriesItsArcCenterAndPoints() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.CONTAIN);
        Arc arc = computedArc();
        squad.setContainmentArc(arc);

        String[] fields = rowFor(squad);

        List<String> expectedPoints = new ArrayList<>();
        for (Position point : arc.getPositions()) {
            expectedPoints.add(point.getX() + ":" + point.getY());
        }
        assertEquals(SquadDecisionLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("1600", fields[columnIndex("arc_center_x")]);
        assertEquals("1600", fields[columnIndex("arc_center_y")]);
        assertEquals(String.join(";", expectedPoints), fields[columnIndex("arc_points")]);
        assertEquals(3, fields[columnIndex("arc_points")].split(";").length);
    }

    @Test
    void aSquadNotContainingCarriesNoArc() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.RETREAT);
        squad.setContainmentArc(computedArc());

        String[] fields = rowFor(squad);

        assertEquals("-1", fields[columnIndex("arc_center_x")]);
        assertEquals("-1", fields[columnIndex("arc_center_y")]);
        assertEquals("NONE", fields[columnIndex("arc_points")]);
    }

    @Test
    void leavingContainmentDropsTheArc() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.CONTAIN);
        squad.startContainLock(100);
        squad.setContainmentArc(computedArc());

        squad.clearContainStart();

        assertEquals(null, squad.getContainmentArc());
    }

    @Test
    void registeredSinkReceivesDefenseDecisions() {
        SquadDecisions.register(recorder());
        Squad squad = new Squad();

        SquadDecisions.defenseEvaluated(squad, DefenseEvent.ABANDON, 6, 0, 2, DefenseSim.unsimulated(8, 0.5));

        assertEquals(1, events.size());
        assertEquals("DEFENSE:ABANDON:6:0:2", events.get(0));
    }

    @Test
    void defenseRowsCarryTheWorkerCountsAndTheSimulation() {
        Squad squad = new Squad();
        SquadDecision context = new SquadDecision();
        DefenseSim sim = new DefenseSim(true, 8, 6, 1, 5, 0.5);
        String row = String.join(",", SquadDecisionLogger.defenseIdentityCells("game-1", 6242, squad,
                DefenseEvent.ABANDON, context))
                + "," + String.join(",", SquadDecisionLogger.squadCells(squad, context, true, -1))
                + "," + String.join(",", SquadDecisionLogger.rallyCells(RallyReason.NONE, RallyRelease.NONE))
                + "," + String.join(",", SquadDecisionLogger.defenseCells(6, 0, 2, sim))
                + "," + String.join(",", SquadDecisionLogger.arcCells(squad))
                + "," + String.join(",", SquadDecisionLogger.enemySampleCells(context));
        String[] fields = row.split(",", -1);

        assertEquals(SquadDecisionLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("DEFENSE", fields[columnIndex("squad_type")]);
        assertEquals("DEFENSE_ABANDON", fields[columnIndex("event")]);
        assertEquals("6", fields[columnIndex("defense_candidates")]);
        assertEquals("0", fields[columnIndex("workers_pulled")]);
        assertEquals("2", fields[columnIndex("workers_released")]);
        assertEquals("8", fields[columnIndex("defense_sim_defenders")]);
        assertEquals("6", fields[columnIndex("defense_sim_enemies")]);
        assertEquals("1", fields[columnIndex("defense_sim_defender_survivors")]);
        assertEquals("5", fields[columnIndex("defense_sim_enemy_survivors")]);
        assertEquals("0.5000", fields[columnIndex("defense_win_threshold")]);
    }

    @Test
    void fightSquadRowsLeaveTheDefenseColumnsUnevaluated() {
        String[] fields = rowFor(new GroundSquad());

        assertEquals("-1", fields[columnIndex("workers_pulled")]);
        assertEquals("-1", fields[columnIndex("defense_sim_defenders")]);
        assertEquals("-1", fields[columnIndex("defense_win_threshold")]);
    }

    @Test
    void aSquadThatVanishesWhileRallyingClosesItsEpisode() {
        assertEquals(RallyRelease.DISBANDED, SquadDecisionLogger.releaseOnDisband(SquadStatus.RALLY));
    }

    @Test
    void aSquadThatVanishesInAnyOtherStatusClosesNoRallyEpisode() {
        assertEquals(RallyRelease.NONE, SquadDecisionLogger.releaseOnDisband(SquadStatus.FIGHT));
        assertEquals(RallyRelease.NONE, SquadDecisionLogger.releaseOnDisband(SquadStatus.RETREAT));
        assertEquals(RallyRelease.NONE, SquadDecisionLogger.releaseOnDisband(SquadStatus.CONTAIN));
        assertEquals(RallyRelease.NONE, SquadDecisionLogger.releaseOnDisband(null));
    }
}
