package telemetry;

import bwapi.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import util.TargetScorer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetChoicesTest {

    private final List<TargetScorer.Selection> received = new ArrayList<>();

    private static int columnIndex(String column) {
        String[] columns = TargetChoiceLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] rowFor(UnitType targetType, TargetScorer.Priority tier, int previousTargetId,
                                   UnitType previousTargetType, boolean scoutCapped) {
        List<String> fields = new ArrayList<>(TargetChoiceLogger.attackerCells("game-1", 2400, 17,
                UnitType.Zerg_Mutalisk));
        fields.addAll(TargetChoiceLogger.targetCells(42, targetType, tier, 96, 3));
        fields.addAll(TargetChoiceLogger.previousTargetCells(previousTargetId, previousTargetType));
        fields.add(TargetChoiceLogger.scoutCappedCell(scoutCapped));
        return String.join(",", fields).split(",", -1);
    }

    private static String[] mutaOnOverlordRow() {
        return rowFor(UnitType.Zerg_Overlord, TargetScorer.Priority.NORMAL, 40, UnitType.Zerg_Sunken_Colony, false);
    }

    @AfterEach
    void clearSink() {
        TargetChoices.clear();
    }

    @Test
    void dispatchIsANoOpWithoutASink() {
        TargetChoices.chosen(null, null, new TargetScorer.Selection(null, TargetScorer.Priority.LOW, 1), false);

        assertTrue(received.isEmpty());
    }

    @Test
    void aFighterWithNoTargetReportsItsFirstChoice() {
        TargetChoices.register((attacker, previousTarget, selection, scoutCapped) -> received.add(selection));
        TargetScorer.Selection selection = new TargetScorer.Selection(null, TargetScorer.Priority.ELEVATED, 4);

        TargetChoices.chosen(null, null, selection, false);

        assertEquals(1, received.size());
        assertEquals(TargetScorer.Priority.ELEVATED, received.get(0).getPriority());
        assertEquals(4, received.get(0).getCandidateCount());
    }

    @Test
    void clearStopsDispatch() {
        TargetChoices.register((attacker, previousTarget, selection, scoutCapped) -> received.add(selection));
        TargetChoices.clear();

        TargetChoices.chosen(null, null, new TargetScorer.Selection(null, TargetScorer.Priority.LOW, 1), false);

        assertTrue(received.isEmpty());
    }

    @Test
    void everyRowCarriesExactlyTheHeaderColumnCount() {
        assertEquals(TargetChoiceLogger.HEADER.split(",", -1).length, mutaOnOverlordRow().length);
    }

    @Test
    void aRowRecordsTheAttackerTheChosenTargetAndItsTier() {
        String[] fields = mutaOnOverlordRow();

        assertEquals("17", fields[columnIndex("attacker_id")]);
        assertEquals("Zerg_Mutalisk", fields[columnIndex("attacker_type")]);
        assertEquals("42", fields[columnIndex("target_id")]);
        assertEquals("Zerg_Overlord", fields[columnIndex("target_type")]);
        assertEquals("NORMAL", fields[columnIndex("tier")]);
        assertEquals("96", fields[columnIndex("distance_px")]);
        assertEquals("3", fields[columnIndex("candidate_count")]);
        assertEquals("40", fields[columnIndex("previous_target_id")]);
        assertEquals("Zerg_Sunken_Colony", fields[columnIndex("previous_target_type")]);
    }

    @Test
    void aRowWithNoPreviousTargetCarriesTheSentinels() {
        String[] fields = rowFor(UnitType.Zerg_Drone, TargetScorer.Priority.ELEVATED, -1, null, false);

        assertEquals("-1", fields[columnIndex("previous_target_id")]);
        assertEquals("NONE", fields[columnIndex("previous_target_type")]);
    }

    @Test
    void aRowRecordsWhetherTheScoutCapRemovedAScout() {
        String[] capped = rowFor(UnitType.Protoss_Zealot, TargetScorer.Priority.CRITICAL, -1, null, true);
        String[] uncapped = mutaOnOverlordRow();

        assertEquals("1", capped[columnIndex("scout_capped")]);
        assertEquals("0", uncapped[columnIndex("scout_capped")]);
    }

    @Test
    void theSinkReceivesTheScoutCappedFlag() {
        List<Boolean> flags = new ArrayList<>();
        TargetChoices.register((attacker, previousTarget, selection, scoutCapped) -> flags.add(scoutCapped));

        TargetChoices.chosen(null, null, new TargetScorer.Selection(null, TargetScorer.Priority.CRITICAL, 1), true);

        assertEquals(1, flags.size());
        assertTrue(flags.get(0));
    }
}
