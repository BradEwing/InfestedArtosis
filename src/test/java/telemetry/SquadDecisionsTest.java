package telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.squad.CombatSimulator;
import unit.squad.GroundSquad;
import unit.squad.Squad;
import unit.squad.SquadStatus;

import java.util.ArrayList;
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
            public void onContainmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment,
                                               boolean entered) {
                events.add("CONTAIN:" + shouldContain + ":" + canBreakContainment + ":" + entered);
            }
        };
    }

    private static String[] rowFor(Squad squad) {
        return rowFor(squad, "NONE");
    }

    private static String[] rowFor(Squad squad, String suppressedBy) {
        SquadDecision context = new SquadDecision();
        String row = String.join(",", SquadDecisionLogger.identityCells("game-1", 1000, squad, "STATUS_CHANGE",
                SquadStatus.RETREAT, SquadStatus.FIGHT, context, suppressedBy))
                + "," + String.join(",", SquadDecisionLogger.squadCells(squad, context, false, -1));
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
        assertEquals(28, columnIndex("committed"));
        assertEquals(29, columnIndex("commit_frame"));

        assertEquals(columnIndex("fight_lock_until_frame") + 1, columnIndex("committed"));
        assertEquals(columnIndex("commit_frame") + 1, columnIndex("should_contain"));
    }

    @Test
    void everyRowCarriesExactlyTheHeaderColumnCount() {
        String[] fields = rowFor(new GroundSquad());

        assertEquals(SquadDecisionLogger.HEADER.split(",", -1).length, fields.length);
    }

    @Test
    void unevaluatedBandStrengthsUseTheNegativeOneSentinel() {
        String[] fields = rowFor(new GroundSquad());

        assertEquals("-1.0000", fields[columnIndex("sim_enemy_strength_0_320")]);
        assertEquals("-1.0000", fields[columnIndex("sim_enemy_strength_321_480")]);
        assertEquals("-1.0000", fields[columnIndex("sim_enemy_strength_481_640")]);
        assertEquals("-1.0000", fields[columnIndex("sim_enemy_strength_excluded_0_320")]);
        assertEquals("-1.0000", fields[columnIndex("sim_enemy_strength_excluded_321_480")]);
        assertEquals("-1.0000", fields[columnIndex("sim_enemy_strength_excluded_481_640")]);
        assertEquals("-1.0000", fields[columnIndex("sim_enemy_strength_excluded_gt_640")]);
    }

    @Test
    void computedEmptyBandsRemainDistinctFromUnevaluatedBands() {
        SquadDecision context = new SquadDecision();
        context.setEnemyStrengthInner(0);
        context.setEnemyStrengthMiddle(0);
        context.setEnemyStrengthOuter(0);
        context.setExcludedEnemyStrengthInner(0);
        context.setExcludedEnemyStrengthMiddle(0);
        context.setExcludedEnemyStrengthOuter(0);
        context.setExcludedEnemyStrengthBeyond(0);
        List<String> fields = SquadDecisionLogger.squadCells(new GroundSquad(), context, false, -1);
        int identityColumns = SquadDecisionLogger.identityCells("game-1", 1000, new GroundSquad(),
                "STATUS_CHANGE", SquadStatus.RETREAT, SquadStatus.FIGHT, context, "NONE").size();

        assertEquals("0.0000", fields.get(columnIndex("sim_enemy_strength_0_320") - identityColumns));
        assertEquals("0.0000", fields.get(columnIndex("sim_enemy_strength_321_480") - identityColumns));
        assertEquals("0.0000", fields.get(columnIndex("sim_enemy_strength_481_640") - identityColumns));
        assertEquals("0.0000", fields.get(columnIndex("sim_enemy_strength_excluded_0_320") - identityColumns));
        assertEquals("0.0000", fields.get(columnIndex("sim_enemy_strength_excluded_321_480") - identityColumns));
        assertEquals("0.0000", fields.get(columnIndex("sim_enemy_strength_excluded_481_640") - identityColumns));
        assertEquals("0.0000", fields.get(columnIndex("sim_enemy_strength_excluded_gt_640") - identityColumns));
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
}
