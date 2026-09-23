package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import unit.squad.RunbyEvaluator;
import unit.squad.RunbyState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunbyLoggerTest {

    @AfterEach
    void clearSink() {
        RunbyTelemetry.clear();
    }

    private static RunbyTick tick(int frame) {
        return RunbyTick.builder()
                .frame(frame)
                .squadId("squad-1")
                .event(RunbyTick.Event.TICK)
                .phase(RunbyState.Phase.PENETRATE)
                .goalType(RunbyState.GoalType.LIKELY)
                .seekPoint(new Position(3600, 400))
                .anchor(new Position(3600, 400))
                .abortWindowOpen(1)
                .enemyTally(0)
                .ourTally(60)
                .inBaseArea(0)
                .lings(8)
                .workersVisible(0)
                .exposedLings(0)
                .hpLost(0)
                .hpLostNonWorkerInReach(0)
                .basesUnderAttack(1)
                .workersKilled(0)
                .buildingsKilled(0)
                .build();
    }

    private static RunbyTick entryCheck(int frame, RunbyEvaluator.EntryVerdict verdict) {
        return RunbyTick.builder()
                .frame(frame)
                .squadId("squad-1")
                .event(RunbyTick.Event.ENTRY_CHECK)
                .verdict(verdict)
                .lings(8)
                .build();
    }

    private static List<String[]> rows(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(RunbyLogger.HEADER, lines.get(0));
        List<String[]> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            rows.add(line.split(",", -1));
        }
        return rows;
    }

    private static int columnIndex(String column) {
        String[] columns = RunbyLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void everyDecisionTickWritesARow(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(RunbyLogger.FILE);
        RunbyLogger logger = new RunbyLogger(null, "game-1", new TelemetryWriter(file, RunbyLogger.HEADER));
        RunbyTelemetry.register(logger);

        RunbyTelemetry.tick(tick(7200));
        RunbyTelemetry.tick(tick(7212));
        RunbyTelemetry.tick(tick(7224));
        logger.onEnd();

        List<String[]> rows = rows(file);
        assertEquals(3, rows.size());
        assertEquals("7200", rows.get(0)[columnIndex("frame")]);
        assertEquals("7212", rows.get(1)[columnIndex("frame")]);
        assertEquals("7224", rows.get(2)[columnIndex("frame")]);
    }

    @Test
    void aTickRowCarriesTheHeaderColumnCountAndItsValues() {
        String[] fields = RunbyLogger.row("game-1", tick(7200)).split(",", -1);

        assertEquals(RunbyLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("TICK", fields[columnIndex("event")]);
        assertEquals("NONE", fields[columnIndex("verdict")]);
        assertEquals("PENETRATE", fields[columnIndex("phase")]);
        assertEquals("LIKELY", fields[columnIndex("goal_type")]);
        assertEquals("3600", fields[columnIndex("seek_x")]);
        assertEquals("400", fields[columnIndex("seek_y")]);
        assertEquals("8", fields[columnIndex("lings")]);
        assertEquals("1", fields[columnIndex("bases_under_attack")]);
        assertEquals("-1", fields[columnIndex("winnable")]);
    }

    @Test
    void anEntryCheckIsWrittenOnlyWhenTheVerdictChanges(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(RunbyLogger.FILE);
        RunbyLogger logger = new RunbyLogger(null, "game-1", new TelemetryWriter(file, RunbyLogger.HEADER));
        RunbyTelemetry.register(logger);

        RunbyTelemetry.tick(entryCheck(7200, RunbyEvaluator.EntryVerdict.ARMY_NEAR_TARGET));
        RunbyTelemetry.tick(entryCheck(7212, RunbyEvaluator.EntryVerdict.ARMY_NEAR_TARGET));
        RunbyTelemetry.tick(entryCheck(7224, RunbyEvaluator.EntryVerdict.ENTER));
        logger.onEnd();

        List<String[]> rows = rows(file);
        assertEquals(2, rows.size());
        assertEquals("ARMY_NEAR_TARGET", rows.get(0)[columnIndex("verdict")]);
        assertEquals("ENTER", rows.get(1)[columnIndex("verdict")]);
        assertEquals("-1", rows.get(1)[columnIndex("seek_x")]);
    }
}
