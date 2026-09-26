package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import unit.squad.AirHarassEvaluator;
import unit.squad.AirHarassState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                .exitReason(AirHarassEvaluator.ExitReason.AA_ARRIVED)
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
        assertEquals("AA_ARRIVED", fields[columnIndex("exit_reason")]);
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
