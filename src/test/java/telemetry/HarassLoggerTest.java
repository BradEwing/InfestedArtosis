package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import unit.squad.AirHarassEvaluator;
import unit.squad.AirHarassState;
import unit.squad.AirHarassTargeting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarassLoggerTest {

    @AfterEach
    void clearSink() {
        HarassTelemetry.clear();
    }

    private static HarassRow entryCheck(int frame, AirHarassEvaluator.EntryVerdict verdict) {
        return HarassRow.builder()
                .frame(frame)
                .squadId("squad-1")
                .event(HarassRow.Event.ENTRY_CHECK)
                .verdict(verdict)
                .mutas(9)
                .build();
    }

    private static HarassRow event(int frame, HarassRow.Event event) {
        return HarassRow.builder()
                .frame(frame)
                .squadId("squad-1")
                .event(event)
                .build();
    }

    private static List<String[]> rows(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(HarassLogger.HEADER, lines.get(0));
        List<String[]> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            rows.add(line.split(",", -1));
        }
        return rows;
    }

    private static void assertColumnsInOrder(String... names) {
        for (int i = 1; i < names.length; i++) {
            assertTrue(columnIndex(names[i - 1]) < columnIndex(names[i]), names[i - 1] + " before " + names[i]);
        }
    }

    private static int columnIndex(String column) {
        String[] columns = HarassLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void anExitRowCarriesTheHeaderColumnCountTheReasonAndTheTallies() {
        HarassRow exit = HarassRow.builder()
                .frame(13000)
                .squadId("squad-1")
                .event(HarassRow.Event.EXIT)
                .exitReason(AirHarassEvaluator.ExitReason.STRIKE_DEFENDED)
                .phase(AirHarassState.Phase.STRIKE)
                .base(new Position(320, 3792))
                .strikePoint(new Position(400, 3700))
                .mutas(7)
                .workersKilled(6)
                .buildingsKilled(1)
                .otherKilled(0)
                .mutasLost(2)
                .build();

        String[] fields = HarassLogger.row("game-1", exit).split(",", -1);

        assertEquals(HarassLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("EXIT", fields[columnIndex("event")]);
        assertEquals("STRIKE_DEFENDED", fields[columnIndex("exit_reason")]);
        assertEquals("NONE", fields[columnIndex("verdict")]);
        assertEquals("STRIKE", fields[columnIndex("phase")]);
        assertEquals("320", fields[columnIndex("base_x")]);
        assertEquals("3700", fields[columnIndex("strike_y")]);
        assertEquals("-1", fields[columnIndex("center_x")]);
        assertEquals("6", fields[columnIndex("workers_killed")]);
        assertEquals("1", fields[columnIndex("buildings_killed")]);
        assertEquals("2", fields[columnIndex("mutas_lost")]);
        assertEquals("-1", fields[columnIndex("healthy_mutas")]);
    }

    @Test
    void anExitRowWritesItsAirDefenseTargetKindAndFlockDefenseAfterTheOriginalColumns() {
        HarassRow exit = HarassRow.builder()
                .frame(13000)
                .squadId("squad-1")
                .event(HarassRow.Event.EXIT)
                .exitReason(AirHarassEvaluator.ExitReason.FLOCK_DEFENDED)
                .strikePoint(new Position(2000, 2000))
                .airDefense(40.5)
                .flockDefense(81)
                .targetKind(HarassRow.TargetKind.EXPOSED)
                .build();

        String[] fields = HarassLogger.row("game-1", exit).split(",", -1);
        String[] columns = HarassLogger.HEADER.split(",", -1);

        assertEquals(columns.length, fields.length);
        assertColumnsInOrder("bases_under_attack", "target_kind", "flock_defense");
        assertEquals("FLOCK_DEFENDED", fields[columnIndex("exit_reason")]);
        assertEquals("EXPOSED", fields[columnIndex("target_kind")]);
        assertEquals("-1", fields[columnIndex("base_x")]);
        assertEquals(40.5, Double.parseDouble(fields[columnIndex("air_defense")]), 1e-9);
        assertEquals(81, Double.parseDouble(fields[columnIndex("flock_defense")]), 1e-9);
    }

    @Test
    void anEntryCheckCarriesTheAntiAirSightingAgeAfterTheFlockDefense() {
        HarassRow check = HarassRow.builder()
                .frame(10296)
                .squadId("squad-1")
                .event(HarassRow.Event.ENTRY_CHECK)
                .verdict(AirHarassEvaluator.EntryVerdict.ENTER)
                .aaSightingAge(10296)
                .build();

        String[] columns = HarassLogger.HEADER.split(",", -1);
        String[] fields = HarassLogger.row("game-1", check).split(",", -1);

        assertEquals(columnIndex("flock_defense") + 1, columnIndex("aa_sighting_age"));
        assertEquals(columns.length, fields.length);
        assertEquals("10296", fields[columnIndex("aa_sighting_age")]);
        assertEquals("ENTER", fields[columnIndex("verdict")]);
        assertEquals("-1", HarassLogger.row("game-1", event(10300, HarassRow.Event.KILL))
                .split(",", -1)[columnIndex("aa_sighting_age")]);
    }

    @Test
    void theKnownCoverAndTheAntiAirReactionAreAppendedAfterTheSightingAge() {
        HarassRow entryCheck = HarassRow.builder()
                .frame(10296)
                .squadId("squad-1")
                .event(HarassRow.Event.ENTRY_CHECK)
                .aaSightingAge(10296)
                .aaKnownCover(1)
                .build();

        String[] columns = HarassLogger.HEADER.split(",", -1);
        String[] check = HarassLogger.row("game-1", entryCheck).split(",", -1);

        assertColumnsInOrder("aa_sighting_age", "prober_hp", "prober_peak_hp", "prober_id", "aa_known_cover",
                "stalled", "exposed_score", "base_score", "aa_seen_frame", "aa_turn_frame", "aa_hp_lost",
                "aa_trigger_type", "aa_trigger_id", "aa_at_target");
        assertEquals(columns.length, check.length);
        assertEquals("1", check[columnIndex("aa_known_cover")]);
        assertEquals("-1", check[columnIndex("prober_id")]);
        assertEquals("-1", check[columnIndex("aa_seen_frame")]);
        assertEquals("-1", check[columnIndex("aa_turn_frame")]);
        assertEquals("-1", check[columnIndex("aa_hp_lost")]);
        assertEquals("NONE", check[columnIndex("aa_trigger_type")]);
        assertEquals("-1", check[columnIndex("aa_trigger_id")]);
        assertEquals("-1", check[columnIndex("aa_at_target")]);
    }

    @Test
    void anAntiAirReactionRowCarriesTheFrameSeenTheFrameTurnedAndTheHitPointsLostBetween() {
        HarassRow reaction = HarassRow.builder()
                .frame(11040)
                .squadId("squad-1")
                .event(HarassRow.Event.AA_REACTION)
                .aaSeenFrame(11032)
                .aaTurnFrame(11040)
                .aaHitPointsLost(24)
                .aaTriggerType(UnitType.Terran_Goliath)
                .aaTriggerId(301)
                .aaAtTarget(0)
                .build();

        String[] fields = HarassLogger.row("game-1", reaction).split(",", -1);

        assertEquals("AA_REACTION", fields[columnIndex("event")]);
        assertEquals("11032", fields[columnIndex("aa_seen_frame")]);
        assertEquals("11040", fields[columnIndex("aa_turn_frame")]);
        assertEquals("24", fields[columnIndex("aa_hp_lost")]);
        assertEquals("Terran_Goliath", fields[columnIndex("aa_trigger_type")]);
        assertEquals("301", fields[columnIndex("aa_trigger_id")]);
        assertEquals("0", fields[columnIndex("aa_at_target")]);
        assertEquals("-1", fields[columnIndex("aa_known_cover")]);
    }

    @Test
    void theEdgeTurretAndRetargetColumnsFollowTheReactionColumnsAndStartAtMinusOne() {
        HarassRow tick = HarassRow.builder().frame(100).squadId("squad-1").event(HarassRow.Event.TICK).build();

        String[] fields = HarassLogger.row("game-1", tick).split(",", -1);

        assertColumnsInOrder("aa_trigger_id", "aa_at_target", "edge_turrets", "edge_turret_id", "retarget_old_id",
                "retarget_old_type", "retarget_new_id", "retarget_new_type");
        assertEquals(HarassLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("-1", fields[columnIndex("edge_turrets")]);
        assertEquals("-1", fields[columnIndex("edge_turret_id")]);
        assertEquals("-1", fields[columnIndex("retarget_old_id")]);
        assertEquals("NONE", fields[columnIndex("retarget_old_type")]);
        assertEquals("-1", fields[columnIndex("retarget_new_id")]);
        assertEquals("NONE", fields[columnIndex("retarget_new_type")]);
        assertColumnsInOrder("retarget_new_type", "retarget_old_distance", "retarget_new_distance",
                "retarget_old_tier", "retarget_new_tier", "defense_zones", "zone_units");
        assertEquals(Csv.format(-1), fields[columnIndex("retarget_old_distance")]);
        assertEquals(Csv.format(-1), fields[columnIndex("retarget_new_distance")]);
        assertEquals("NONE", fields[columnIndex("retarget_old_tier")]);
        assertEquals("NONE", fields[columnIndex("retarget_new_tier")]);
        assertEquals("-1", fields[columnIndex("defense_zones")]);
        assertEquals("-1", fields[columnIndex("zone_units")]);
    }

    @Test
    void aUnitRetargetRowCarriesTheDistancesAndTiersOfBothTargets() {
        HarassRow retarget = HarassRow.builder()
                .frame(11100)
                .squadId("squad-1")
                .event(HarassRow.Event.UNIT_RETARGET)
                .retargetOldDistance(212.5)
                .retargetNewDistance(96)
                .retargetOldTier(AirHarassTargeting.Tier.PRODUCTION)
                .retargetNewTier(AirHarassTargeting.Tier.WORKER)
                .build();
        HarassRow tick = HarassRow.builder().frame(11200).squadId("squad-1").event(HarassRow.Event.TICK)
                .defenseZones(2).zoneUnits(5).build();

        String[] fields = HarassLogger.row("game-1", retarget).split(",", -1);
        String[] tickFields = HarassLogger.row("game-1", tick).split(",", -1);

        assertEquals(HarassLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals(Csv.format(212.5), fields[columnIndex("retarget_old_distance")]);
        assertEquals(Csv.format(96), fields[columnIndex("retarget_new_distance")]);
        assertEquals("PRODUCTION", fields[columnIndex("retarget_old_tier")]);
        assertEquals("WORKER", fields[columnIndex("retarget_new_tier")]);
        assertEquals("2", tickFields[columnIndex("defense_zones")]);
        assertEquals("5", tickFields[columnIndex("zone_units")]);
        assertEquals("-1", tickFields[columnIndex("zones_cleared")]);
    }

    @Test
    void zoneRowsCarryTheZoneCountsAndTheClearsAfterMainsColumns() {
        HarassRow record = HarassRow.builder().frame(11300).squadId("squad-1").event(HarassRow.Event.ZONE_RECORD)
                .aaAtTarget(1).defenseZones(3).zoneUnits(4).build();
        HarassRow clear = HarassRow.builder().frame(11400).event(HarassRow.Event.ZONE_CLEAR)
                .defenseZones(1).zonesCleared(2).build();

        String[] recordFields = HarassLogger.row("game-1", record).split(",", -1);
        String[] clearFields = HarassLogger.row("game-1", clear).split(",", -1);

        assertEquals(HarassLogger.HEADER.split(",", -1).length, recordFields.length);
        assertEquals(HarassLogger.HEADER.split(",", -1).length, clearFields.length);
        assertEquals("ZONE_RECORD", recordFields[columnIndex("event")]);
        assertEquals("1", recordFields[columnIndex("aa_at_target")]);
        assertEquals("4", recordFields[columnIndex("zone_units")]);
        assertEquals("ZONE_CLEAR", clearFields[columnIndex("event")]);
        assertEquals("NONE", clearFields[columnIndex("squad_id")]);
        assertEquals("2", clearFields[columnIndex("zones_cleared")]);
        assertColumnsInOrder("zone_units", "zones_cleared");
    }

    @Test
    void aUnitRetargetRowNamesTheTargetLeftAndTheTargetTaken() {
        HarassRow retarget = HarassRow.builder()
                .frame(11100)
                .squadId("squad-1")
                .event(HarassRow.Event.UNIT_RETARGET)
                .retargetOldId(41)
                .retargetOldType(UnitType.Terran_Barracks)
                .retargetNewId(52)
                .retargetNewType(UnitType.Terran_SCV)
                .build();
        HarassRow turret = HarassRow.builder()
                .frame(11120)
                .squadId("squad-1")
                .event(HarassRow.Event.EDGE_TURRET)
                .edgeTurrets(1)
                .edgeTurretId(77)
                .build();

        String[] retargetFields = HarassLogger.row("game-1", retarget).split(",", -1);
        String[] turretFields = HarassLogger.row("game-1", turret).split(",", -1);

        assertEquals("UNIT_RETARGET", retargetFields[columnIndex("event")]);
        assertEquals("41", retargetFields[columnIndex("retarget_old_id")]);
        assertEquals("Terran_Barracks", retargetFields[columnIndex("retarget_old_type")]);
        assertEquals("52", retargetFields[columnIndex("retarget_new_id")]);
        assertEquals("Terran_SCV", retargetFields[columnIndex("retarget_new_type")]);
        assertEquals("EDGE_TURRET", turretFields[columnIndex("event")]);
        assertEquals("1", turretFields[columnIndex("edge_turrets")]);
        assertEquals("77", turretFields[columnIndex("edge_turret_id")]);
    }

    @Test
    void aRepeatedEntryVerdictIsWrittenAgainWhenTheStalledCellChanges(@TempDir Path directory)
            throws IOException {
        Path file = directory.resolve(HarassLogger.FILE);
        HarassLogger logger = new HarassLogger(null, "game-1", new TelemetryWriter(file, HarassLogger.HEADER));
        HarassTelemetry.register(logger);

        for (int stalled : new int[] {0, 0, 1, 1}) {
            HarassTelemetry.row(HarassRow.builder().frame(100 + stalled).squadId("squad-1")
                    .event(HarassRow.Event.ENTRY_CHECK).verdict(AirHarassEvaluator.EntryVerdict.NO_TARGET)
                    .stalled(stalled).build());
        }
        logger.onEnd();

        List<String[]> rows = rows(file);
        assertEquals(2, rows.size());
        assertEquals("0", rows.get(0)[columnIndex("stalled")]);
        assertEquals("1", rows.get(1)[columnIndex("stalled")]);
    }

    @Test
    void stalledAndScoreCellsAppendAfterTheExistingColumnsAndReadMinusOneWhenNotEvaluated() {
        HarassRow entryCheck = HarassRow.builder()
                .frame(10296)
                .squadId("squad-1")
                .event(HarassRow.Event.ENTRY_CHECK)
                .stalled(1)
                .exposedScore(312.5)
                .baseScore(90)
                .build();
        HarassRow tick = HarassRow.builder()
                .frame(10320)
                .squadId("squad-1")
                .event(HarassRow.Event.TICK)
                .build();

        String[] columns = HarassLogger.HEADER.split(",", -1);
        String[] check = HarassLogger.row("game-1", entryCheck).split(",", -1);
        String[] fields = HarassLogger.row("game-1", tick).split(",", -1);

        assertColumnsInOrder("aa_known_cover", "stalled", "exposed_score", "base_score");
        assertEquals(columns.length, check.length);
        assertEquals("1", check[columnIndex("stalled")]);
        assertEquals(Csv.format(312.5), check[columnIndex("exposed_score")]);
        assertEquals(Csv.format(90.0), check[columnIndex("base_score")]);
        assertEquals("-1", fields[columnIndex("stalled")]);
        assertEquals(Csv.format(-1.0), fields[columnIndex("exposed_score")]);
        assertEquals(Csv.format(-1.0), fields[columnIndex("base_score")]);
    }

    @Test
    void aKillRowNamesTheKilledType() {
        HarassRow kill = HarassRow.builder()
                .frame(13000)
                .squadId("squad-1")
                .event(HarassRow.Event.KILL)
                .killedType(UnitType.Terran_SCV)
                .build();

        String[] fields = HarassLogger.row("game-1", kill).split(",", -1);

        assertEquals("Terran_SCV", fields[columnIndex("killed_type")]);
    }

    @Test
    void anEntryCheckIsWrittenOnlyWhenTheVerdictChangesAndAgainAfterAnEntry(@TempDir Path directory)
            throws IOException {
        Path file = directory.resolve(HarassLogger.FILE);
        HarassLogger logger = new HarassLogger(null, "game-1", new TelemetryWriter(file, HarassLogger.HEADER));
        HarassTelemetry.register(logger);

        HarassTelemetry.row(entryCheck(12000, AirHarassEvaluator.EntryVerdict.TOO_FEW));
        HarassTelemetry.row(entryCheck(12012, AirHarassEvaluator.EntryVerdict.TOO_FEW));
        HarassTelemetry.row(entryCheck(12024, AirHarassEvaluator.EntryVerdict.ENTER));
        HarassTelemetry.row(event(12024, HarassRow.Event.ENTER));
        HarassTelemetry.row(event(12036, HarassRow.Event.TICK));
        HarassTelemetry.row(event(12400, HarassRow.Event.EXIT));
        HarassTelemetry.row(entryCheck(12600, AirHarassEvaluator.EntryVerdict.ENTER));
        logger.onEnd();

        List<String[]> rows = rows(file);
        assertEquals(6, rows.size());
        assertEquals("TOO_FEW", rows.get(0)[columnIndex("verdict")]);
        assertEquals("ENTER", rows.get(1)[columnIndex("verdict")]);
        assertEquals("ENTER", rows.get(2)[columnIndex("event")]);
        assertEquals("EXIT", rows.get(4)[columnIndex("event")]);
        assertEquals("12600", rows.get(5)[columnIndex("frame")]);
    }
}
