package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.squad.CombatSimulator;
import unit.squad.DefenseSim;
import unit.squad.GroundSquad;
import unit.squad.RunbyState;
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

    private static final int IDENTITY_CELLS = 9;

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
            public void onPathTaken(Squad squad, DecisionPath path) {
                events.add("PATH:" + path);
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
            public void onRunbyPhaseStarted(Squad squad, RunbyState.Phase from, RunbyState.Phase to,
                                            DecisionPath path) {
                events.add("PHASE:" + from + ":" + to + ":" + path);
            }

            @Override
            public void onContainmentPushedBack(Squad squad, Position from, Position to, UnitType enemyType,
                                                int membersMoved) {
                events.add("PUSHBACK:" + from + ":" + to + ":" + enemyType + ":" + membersMoved);
            }

            @Override
            public void onContainmentEnded(Squad squad, int supplyLost) {
                events.add("CONTAIN_ENDED:" + supplyLost);
            }

            @Override
            public void onOutrangedHitEvaluated(Squad squad, boolean outrangedHit) {
                events.add("OUTRANGED_HIT:" + outrangedHit);
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
                + "," + String.join(",", SquadDecisionLogger.squadCells(squad, context, false, -1, 1000))
                + "," + String.join(",", SquadDecisionLogger.rallyCells(reason, release))
                + "," + String.join(",", SquadDecisionLogger.defenseCells(-1, -1, -1, null))
                + "," + String.join(",", SquadDecisionLogger.arcCells(squad))
                + "," + String.join(",", SquadDecisionLogger.pathCells(context))
                + "," + String.join(",", SquadDecisionLogger.enemySampleCells(context))
                + "," + String.join(",", SquadDecisionLogger.runbyCells(null, null))
                + "," + String.join(",", SquadDecisionLogger.containmentCells(context));
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
    void registeredSinkReceivesThePushBackAndTheEpisodeEnd() {
        SquadDecisions.register(recorder());
        Squad squad = new GroundSquad();

        SquadDecisions.containmentPushedBack(squad, new Position(1600, 1440), new Position(1600, 1376),
                UnitType.Protoss_Dragoon, 12);
        SquadDecisions.containmentEnded(squad, 18);

        assertEquals(2, events.size());
        assertTrue(events.get(0).startsWith("PUSHBACK:"));
        assertTrue(events.get(0).endsWith(":Protoss_Dragoon:12"));
        assertEquals("CONTAIN_ENDED:18", events.get(1));
    }

    @Test
    void containmentDispatchIsANoOpWithoutASink() {
        Squad squad = new GroundSquad();

        SquadDecisions.containmentPushedBack(squad, null, null, UnitType.Terran_Marine, 1);
        SquadDecisions.containmentEnded(squad, 4);

        assertTrue(events.isEmpty());
    }

    @Test
    void aPushBackRowCarriesBothMidpointsTheEnemyAndTheMembersMoved() {
        SquadDecision context = new SquadDecision();
        context.setPushbackFrom(new Position(1600, 1440));
        context.setPushbackTo(new Position(1600, 1376));
        context.setPushbackEnemyType(UnitType.Terran_Siege_Tank_Siege_Mode);
        context.setPushbackMembersMoved(20);

        List<String> cells = SquadDecisionLogger.containmentCells(context);
        String[] columns = SquadDecisionLogger.HEADER.split(",", -1);
        int first = java.util.Arrays.asList(columns).indexOf("pushback_from_x");

        assertEquals(columns.length - first, cells.size());
        assertEquals("1600", cells.get(columnIndex("pushback_from_x") - first));
        assertEquals("1440", cells.get(columnIndex("pushback_from_y") - first));
        assertEquals("1600", cells.get(columnIndex("pushback_to_x") - first));
        assertEquals("1376", cells.get(columnIndex("pushback_to_y") - first));
        assertEquals("Terran_Siege_Tank_Siege_Mode", cells.get(columnIndex("pushback_enemy_type") - first));
        assertEquals("20", cells.get(columnIndex("pushback_members_moved") - first));
        assertEquals("-1", cells.get(columnIndex("contain_supply_lost") - first));
    }

    @Test
    void aContainingSquadsRowCarriesWhetherAMemberTookAnOutrangedHit() {
        SquadDecision hit = new SquadDecision();
        hit.setOutrangedHit(SquadDecision.tristate(true));
        SquadDecision quiet = new SquadDecision();
        quiet.setOutrangedHit(SquadDecision.tristate(false));
        int first = columnIndex("pushback_from_x");

        assertEquals("1", SquadDecisionLogger.containmentCells(hit).get(columnIndex("outranged_hit") - first));
        assertEquals("0", SquadDecisionLogger.containmentCells(quiet).get(columnIndex("outranged_hit") - first));
        assertEquals("-1", rowFor(new GroundSquad())[columnIndex("outranged_hit")]);
    }

    @Test
    void registeredSinkReceivesOutrangedHitEvaluations() {
        SquadDecisions.register(recorder());

        SquadDecisions.outrangedHit(new Squad(), true);

        assertEquals(Collections.singletonList("OUTRANGED_HIT:true"), events);
    }

    @Test
    void theExitRowCarriesTheSupplyLostInRealSupply() {
        SquadDecision context = new SquadDecision();
        context.setContainSupplyLost(19);

        List<String> cells = SquadDecisionLogger.containmentCells(context);
        int first = columnIndex("pushback_from_x");

        assertEquals("9.5", cells.get(columnIndex("contain_supply_lost") - first));
        assertEquals("-1", cells.get(columnIndex("pushback_from_x") - first));
        assertEquals("NONE", cells.get(columnIndex("pushback_enemy_type") - first));
    }

    @Test
    void anOrdinaryRowLeavesTheContainmentColumnsUnevaluated() {
        String[] fields = rowFor(new GroundSquad());

        assertEquals("-1", fields[columnIndex("pushback_from_x")]);
        assertEquals("NONE", fields[columnIndex("pushback_enemy_type")]);
        assertEquals("-1", fields[columnIndex("pushback_members_moved")]);
        assertEquals("-1", fields[columnIndex("contain_supply_lost")]);
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
                + "," + String.join(",", SquadDecisionLogger.squadCells(squad, context, true, -1, 6242))
                + "," + String.join(",", SquadDecisionLogger.rallyCells(RallyReason.NONE, RallyRelease.NONE))
                + "," + String.join(",", SquadDecisionLogger.defenseCells(6, 0, 2, sim))
                + "," + String.join(",", SquadDecisionLogger.arcCells(squad))
                + "," + String.join(",", SquadDecisionLogger.pathCells(context))
                + "," + String.join(",", SquadDecisionLogger.enemySampleCells(context))
                + "," + String.join(",", SquadDecisionLogger.runbyCells(null, null))
                + "," + String.join(",", SquadDecisionLogger.containmentCells(context));
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
    void aContainingSquadThatVanishesCarriesItsEpisodeSupplyLost() {
        SquadDecision pending = new SquadDecision();
        pending.setContainSupplyLost(44);

        SquadDecision context = SquadDecisionLogger.disbandContext(SquadStatus.CONTAIN, pending);

        assertEquals(44, context.getContainSupplyLost());
        assertEquals(RallyRelease.NONE, context.getRallyRelease());
    }

    @Test
    void aSquadThatVanishesWithNothingPendingReportsNoSupplyLost() {
        SquadDecision context = SquadDecisionLogger.disbandContext(SquadStatus.RALLY, null);

        assertEquals(SquadDecision.NOT_EVALUATED, context.getContainSupplyLost());
        assertEquals(RallyRelease.DISBANDED, context.getRallyRelease());
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

    @Test
    void aRunbyPhaseChangeReachesTheSink() {
        SquadDecisions.register(recorder());

        SquadDecisions.runbyPhaseStarted(new GroundSquad(), RunbyState.Phase.PENETRATE, RunbyState.Phase.HARASS,
                DecisionPath.RUNBY_PHASE);

        assertEquals(1, events.size());
        assertEquals("PHASE:PENETRATE:HARASS:RUNBY_PHASE", events.get(0));
    }

    @Test
    void aRunbyPhaseChangeIsANoOpWithoutASink() {
        SquadDecisions.runbyPhaseStarted(new GroundSquad(), RunbyState.Phase.PENETRATE, RunbyState.Phase.HARASS,
                DecisionPath.RUNBY_PHASE);

        assertTrue(events.isEmpty());
    }

    @Test
    void aPhaseChangeRowKeepsTheStatusAndNamesBothPhases() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.RUNBY);
        SquadDecision context = new SquadDecision();
        context.setDecisionPath(DecisionPath.RUNBY_PHASE);
        String row = String.join(",", SquadDecisionLogger.identityCells("game-1", 7000, squad,
                SquadDecisionLogger.EVENT_PHASE_CHANGE, squad.getStatus(), squad.getStatus(), context, "NONE"))
                + "," + String.join(",", SquadDecisionLogger.squadCells(squad, context, true, -1, 7000))
                + "," + String.join(",", SquadDecisionLogger.rallyCells(RallyReason.NONE, RallyRelease.NONE))
                + "," + String.join(",", SquadDecisionLogger.defenseCells(-1, -1, -1, null))
                + "," + String.join(",", SquadDecisionLogger.arcCells(squad))
                + "," + String.join(",", SquadDecisionLogger.pathCells(context))
                + "," + String.join(",", SquadDecisionLogger.enemySampleCells(context))
                + "," + String.join(",", SquadDecisionLogger.runbyCells(RunbyState.Phase.PENETRATE,
                RunbyState.Phase.HARASS))
                + "," + String.join(",", SquadDecisionLogger.containmentCells(context));
        String[] fields = row.split(",", -1);

        assertEquals(SquadDecisionLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("PHASE_CHANGE", fields[columnIndex("event")]);
        assertEquals("RUNBY", fields[columnIndex("old_status")]);
        assertEquals("RUNBY", fields[columnIndex("new_status")]);
        assertEquals("RUNBY_PHASE", fields[columnIndex("decision_path")]);
        assertEquals("PENETRATE", fields[columnIndex("runby_phase_old")]);
        assertEquals("HARASS", fields[columnIndex("runby_phase")]);
    }

    @Test
    void aRowOutsideARunbyCarriesNoPhase() {
        String[] fields = rowFor(new GroundSquad());

        assertEquals("NONE", fields[columnIndex("runby_phase_old")]);
        assertEquals("NONE", fields[columnIndex("runby_phase")]);
    }

    @Test
    void pathDispatchIsANoOpWithoutASink() {
        SquadDecisions.pathTaken(new GroundSquad(), DecisionPath.NO_VISION_MARCH);

        assertTrue(events.isEmpty());
    }

    @Test
    void registeredSinkReceivesTheDecisionPath() {
        SquadDecisions.register(recorder());

        SquadDecisions.pathTaken(new GroundSquad(), DecisionPath.NO_VISION_MARCH);

        assertEquals(1, events.size());
        assertEquals("PATH:NO_VISION_MARCH", events.get(0));
    }

    @Test
    void decisionPathFollowsTheArcColumns() {
        String[] columns = SquadDecisionLogger.HEADER.split(",", -1);
        int path = java.util.Arrays.asList(columns).indexOf("decision_path");

        assertEquals("arc_points", columns[path - 1]);
        assertEquals("sim_enemy_composition", columns[path + 1]);
    }

    @Test
    void everyRowNamesTheBranchThatDecidedIt() {
        SquadDecision context = new SquadDecision();
        context.setDecisionPath(DecisionPath.NO_VISION_MARCH);

        assertEquals("NO_VISION_MARCH", SquadDecisionLogger.pathCells(context).get(0));
    }

    @Test
    void aRowNoBranchClaimedNamesNone() {
        String[] fields = rowFor(new GroundSquad());

        assertEquals("NONE", fields[columnIndex("decision_path")]);
    }

    @Test
    void lockColumnsAreReadFromTheSquadAtTheRowFrame() {
        Squad locked = new GroundSquad();
        locked.startRetreatLock(1000);

        List<String> fields = SquadDecisionLogger.squadCells(locked, new SquadDecision(), false, -1, 1000);

        assertEquals("1", fields.get(columnIndex("retreat_locked") - IDENTITY_CELLS));
        assertEquals("0", fields.get(columnIndex("fight_locked") - IDENTITY_CELLS));
        assertTrue(locked.getRetreatLockedUntilFrame() > 1000);
    }

    @Test
    void anExpiredLockReportsUnlockedOnTheSameSquad() {
        Squad locked = new GroundSquad();
        locked.startRetreatLock(1000);
        int expiry = locked.getRetreatLockedUntilFrame();

        List<String> fields = SquadDecisionLogger.squadCells(locked, new SquadDecision(), false, -1, expiry);

        assertEquals("0", fields.get(columnIndex("retreat_locked") - IDENTITY_CELLS));
        assertEquals(String.valueOf(expiry), fields.get(columnIndex("retreat_lock_until_frame") - IDENTITY_CELLS));
    }

}
