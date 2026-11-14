package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementTest {

    private static final Position ANCHOR = new Position(1000, 1000);
    private static final String GAME_ID = "game-1";

    private static Engagement engagementAt(int frame) {
        return new Engagement(7, frame, ANCHOR);
    }

    private static String[] engagementFields(Engagement engagement) {
        return engagement.toRow(GAME_ID).split(",", -1);
    }

    @Test
    void engagementRowMatchesTheEngagementHeader() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));
        engagement.noteSupply(1, 12, 8);

        assertEquals(TelemetryLog.ENGAGEMENT_HEADER.split(",", -1).length, engagementFields(engagement).length);
    }

    @Test
    void unitRowMatchesTheUnitHeader() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Hydralisk, 80, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));

        List<String> rows = engagement.toUnitRows(GAME_ID);
        assertEquals(1, rows.size());
        assertEquals(TelemetryLog.ENGAGEMENT_UNIT_HEADER.split(",", -1).length, rows.get(0).split(",", -1).length);
    }

    private static int unitColumn(String column) {
        return Arrays.asList(TelemetryLog.ENGAGEMENT_UNIT_HEADER.split(",", -1)).indexOf(column);
    }

    @Test
    void theAttackColumnsAreAppendedAfterSquadAtArrival() {
        String[] columns = TelemetryLog.ENGAGEMENT_UNIT_HEADER.split(",", -1);

        assertEquals(unitColumn("squad_at_arrival") + 1, unitColumn("first_attack_frame"));
        assertEquals(unitColumn("first_attack_frame") + 1, unitColumn("attacks_started"));
    }

    @Test
    void theDamageColumnIsAppendedAfterTheAttackColumns() {
        String[] columns = TelemetryLog.ENGAGEMENT_UNIT_HEADER.split(",", -1);

        assertEquals(unitColumn("attacks_started") + 1, unitColumn("damage_dealt"));
        assertEquals(unitColumn("damage_dealt") + 1, unitColumn("attack_cycles"));
    }

    @Test
    void theCycleColumnsAreAppendedAfterTheDamageColumnInOrder() {
        List<String> expected = Arrays.asList("attack_cycles", "cycle_frames_sum", "cooldown_frames_in_threat",
                "cooldown_damage_taken", "ready_frames_out_of_range", "in_reach_cycles", "in_reach_cycle_frames_sum",
                "cooldown_frames", "hp_lost_total", "kite_plans", "kite_backoffs", "kite_no_clear", "kite_no_threat",
                "kite_replans", "episode_id", "joiner");
        List<String> columns = Arrays.asList(TelemetryLog.ENGAGEMENT_UNIT_HEADER.split(",", -1));
        int first = unitColumn("damage_dealt") + 1;

        assertEquals(expected, columns.subList(first, columns.size()));
    }

    @Test
    void aUnitKeepsTheStandoffCohortItArrivedWith() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0),
                new StandoffCohort(4, true));
        engagement.noteUnit(108, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-b", AttackTally.of(0, -1, 0),
                new StandoffCohort(9, false));
        engagement.noteUnit(100, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));

        List<String> rows = engagement.toUnitRows(GAME_ID);
        String[] joiner = rows.get(0).split(",", -1);
        String[] untagged = rows.get(1).split(",", -1);
        if (!"1".equals(joiner[unitColumn("unit_id")])) {
            String[] swap = joiner;
            joiner = untagged;
            untagged = swap;
        }

        assertEquals("4", joiner[unitColumn("episode_id")]);
        assertEquals("1", joiner[unitColumn("joiner")]);
        assertEquals("-1", untagged[unitColumn("episode_id")]);
        assertEquals("0", untagged[unitColumn("joiner")]);
    }

    private static AttackTally tally(int attacks, int lastAttack, int damage, int... cycleCounts) {
        int[] counts = new int[CycleColumn.values().length];
        System.arraycopy(cycleCounts, 0, counts, 0, cycleCounts.length);
        return new AttackTally(attacks, lastAttack, damage, counts);
    }

    @Test
    void everyCycleColumnRecordsOnlyItsRiseInsideTheEngagement() {
        Engagement engagement = engagementAt(100);
        int[] arrival = {3, 100, 12, 9, 7, 2, 70, 90, 40, 3, 2, 1, 0, 0};
        int[] exit = {5, 170, 20, 18, 10, 3, 101, 150, 75, 5, 3, 1, 1, 2};
        engagement.noteUnit(100, 1, UnitType.Zerg_Mutalisk, 120, "FIGHT", "squad-a", tally(4, 90, 36, arrival));
        engagement.noteUnit(108, 1, UnitType.Zerg_Mutalisk, 120, "FIGHT", "squad-a", tally(6, 104, 54, exit));

        String[] row = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        for (CycleColumn column : CycleColumn.values()) {
            int rise = exit[column.ordinal()] - arrival[column.ordinal()];
            assertEquals(String.valueOf(rise), row[unitColumn(column.column())], column.column());
        }
        assertEquals("2", row[unitColumn("attack_cycles")]);
        assertEquals("35", row[unitColumn("hp_lost_total")]);
        assertEquals("1", row[unitColumn("kite_no_threat")]);
    }

    @Test
    void aUnitThatDiesBetweenSamplesKeepsTheCyclesItCompletedAfterItsLastSample() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Mutalisk, 120, "FIGHT", "squad-a",
                tally(4, 90, 36, 3, 100, 12, 9, 7, 0, 0, 60, 10));

        engagement.recordDeath(1, 115, new Position(500, 600),
                tally(5, 112, 45, 4, 133, 30, 40, 7, 0, 0, 80, 130));

        String[] row = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("1", row[unitColumn("attack_cycles")]);
        assertEquals("33", row[unitColumn("cycle_frames_sum")]);
        assertEquals("31", row[unitColumn("cooldown_damage_taken")]);
        assertEquals("120", row[unitColumn("hp_lost_total")]);
        assertEquals("20", row[unitColumn("cooldown_frames")]);
    }


    @Test
    void aUnitRecordsOnlyTheDamageItDealtInsideTheEngagement() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(3, 40, 25));
        engagement.noteUnit(108, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(4, 104, 30));
        engagement.noteUnit(116, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(5, 112, 35));

        String[] row = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("10", row[unitColumn("damage_dealt")]);
    }

    @Test
    void aUnitThatDiesBetweenSamplesKeepsTheDamageItDealtAfterItsLastSample() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(3, 40, 25));
        engagement.noteUnit(108, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(3, 40, 25));

        engagement.recordDeath(1, 115, new Position(500, 600), AttackTally.of(4, 111, 30));

        String[] row = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("5", row[unitColumn("damage_dealt")]);
    }

    @Test
    void aUnitThatNeverHitAnythingDealtNoDamage() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(2, 40, 25));
        engagement.noteUnit(108, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(2, 40, 25));

        String[] row = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("0", row[unitColumn("damage_dealt")]);
    }

    @Test
    void aUnitThatDiesBetweenSamplesKeepsTheAttacksItStartedAfterItsLastSample() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(3, 40, 0));
        engagement.noteUnit(108, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(3, 40, 0));

        engagement.recordDeath(1, 115, new Position(500, 600), AttackTally.of(4, 111, 0));

        String[] fields = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("111", fields[unitColumn("first_attack_frame")]);
        assertEquals("1", fields[unitColumn("attacks_started")]);
    }

    @Test
    void aDeathWithoutTheUnitsAttacksKeepsTheSampledCount() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(3, 40, 0));
        engagement.noteUnit(108, 1, UnitType.Zerg_Zergling, 30, "FIGHT", "squad-a", AttackTally.of(5, 104, 0));

        engagement.recordDeath(1, 115, new Position(500, 600));

        String[] fields = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("2", fields[unitColumn("attacks_started")]);
    }

    @Test
    void aUnitThatAttackedRecordsItsFirstAttackAndOnlyTheAttacksStartedInsideTheEngagement() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(3, 40, 0));
        engagement.noteUnit(108, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(3, 40, 0));
        engagement.noteUnit(116, 1, UnitType.Zerg_Zergling, 30, "FIGHT", "squad-a", AttackTally.of(4, 111, 0));
        engagement.noteUnit(124, 1, UnitType.Zerg_Zergling, 20, "FIGHT", "squad-a", AttackTally.of(6, 122, 0));

        String[] row = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("111", row[unitColumn("first_attack_frame")]);
        assertEquals("3", row[unitColumn("attacks_started")]);
    }

    @Test
    void aUnitThatNeverAttackedHasNoFirstAttackFrame() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(2, 40, 0));
        engagement.noteUnit(108, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(2, 40, 0));

        String[] row = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("-1", row[unitColumn("first_attack_frame")]);
        assertEquals("0", row[unitColumn("attacks_started")]);
    }

    @Test
    void arrivalOffsetIsRelativeToTheEngagementStart() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));
        engagement.noteUnit(340, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-b", AttackTally.of(0, -1, 0));

        List<String> rows = engagement.toUnitRows(GAME_ID);
        assertEquals("0", rows.get(0).split(",", -1)[6]);
        assertEquals("240", rows.get(1).split(",", -1)[6]);
    }

    @Test
    void supplyWeightedArrivalQuantilesUseHalfSupply() {
        Engagement engagement = engagementAt(0);
        engagement.noteUnit(0, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));
        engagement.noteUnit(0, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));
        engagement.noteUnit(0, 3, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));
        engagement.noteUnit(480, 4, UnitType.Zerg_Ultralisk, 400, "FIGHT", "squad-b", AttackTally.of(0, -1, 0));

        String[] fields = engagementFields(engagement);
        assertEquals("0", fields[12]);
        assertEquals("480", fields[13]);
        assertEquals("480", fields[14]);
    }

    @Test
    void deathsAreCountedOnceAndInHalfSupply() {
        Engagement engagement = engagementAt(0);
        engagement.noteUnit(0, 1, UnitType.Zerg_Hydralisk, 80, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));
        engagement.noteUnit(0, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));

        engagement.recordDeath(1, 24, new Position(500, 600));
        engagement.recordDeath(1, 32, new Position(500, 600));
        engagement.recordDeath(99, 40, new Position(500, 600));

        assertEquals(1, engagement.getUnitsLost());
        assertEquals(UnitType.Zerg_Hydralisk.supplyRequired(), engagement.getSupplyLost());

        String[] deadUnit = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("1", deadUnit[8]);
        assertEquals("24", deadUnit[9]);
        assertEquals("500", deadUnit[10]);
        assertEquals("600", deadUnit[11]);
    }

    @Test
    void statusChangesCountTransitionsNotSamples() {
        Engagement engagement = engagementAt(0);
        engagement.noteStatus("FIGHT");
        engagement.noteStatus("FIGHT");
        engagement.noteStatus("RETREAT");
        engagement.noteStatus("RETREAT");
        engagement.noteStatus("FIGHT");

        String[] fields = engagementFields(engagement);
        assertEquals("FIGHT", fields[20]);
        assertEquals("FIGHT", fields[21]);
        assertEquals("2", fields[22]);
    }

    @Test
    void absorbKeepsTheOlderStartAndSumsLosses() {
        Engagement first = engagementAt(100);
        first.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));
        first.noteSupply(1, 10, 4);
        first.recordDeath(1, 110, new Position(1, 1));

        Engagement second = new Engagement(8, 200, new Position(1200, 1000));
        second.noteUnit(200, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-b", AttackTally.of(0, -1, 0));
        second.noteSupply(1, 10, 6);
        second.recordDeath(2, 210, new Position(2, 2));

        first.absorb(second);

        assertEquals(100, first.getStartFrame());
        assertEquals(2, first.getUnitsLost());
        assertEquals(2, first.getSupplyLost());

        String[] fields = engagementFields(first);
        assertEquals("2", fields[19]);
        assertEquals("6", fields[15]);
        assertEquals("100", fields[2]);
    }

    @Test
    void absorbedUnitsKeepTheirAbsoluteArrivalAndGetRebasedOffsets() {
        Engagement first = engagementAt(100);
        first.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));

        Engagement second = new Engagement(8, 300, new Position(1200, 1000));
        second.noteUnit(300, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-b", AttackTally.of(0, -1, 0));

        first.absorb(second);

        List<String> rows = first.toUnitRows(GAME_ID);
        assertEquals(2, rows.size());
        assertEquals("300", rows.get(1).split(",", -1)[5]);
        assertEquals("200", rows.get(1).split(",", -1)[6]);
    }

    @Test
    void noRowFieldContainsAComma() {
        Engagement engagement = engagementAt(0);
        engagement.noteUnit(0, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a", AttackTally.of(0, -1, 0));
        engagement.noteSupply(1, 10, 4);
        engagement.noteStatus("FIGHT");
        engagement.noteSim(1.25, 1.4, "RETREAT", "Terran_Marine:4;Terran_Medic:1");

        assertEquals(TelemetryLog.ENGAGEMENT_HEADER.split(",", -1).length, engagementFields(engagement).length);
        for (String field : engagementFields(engagement)) {
            assertTrue(field.indexOf(',') < 0);
        }
    }
}
