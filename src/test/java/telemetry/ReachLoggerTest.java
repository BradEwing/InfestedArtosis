package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReachLoggerTest {

    @AfterEach
    void clearSink() {
        ReachTelemetry.clear();
    }

    private static int columnIndex(String column) {
        String[] columns = ReachLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void aBunkerReachLearnedFromAShotWritesARowWithTheVictim() {
        String[] fields = ReachLogger.row("game-1", 7546, UnitType.Terran_Bunker, 128, 184,
                EnemyReachMemory.Source.BULLET, new Position(831, 1102)).split(",", -1);

        assertEquals(ReachLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("7546", fields[columnIndex("frame")]);
        assertEquals("Terran_Bunker", fields[columnIndex("unit_type")]);
        assertEquals("128", fields[columnIndex("old_reach")]);
        assertEquals("184", fields[columnIndex("new_reach")]);
        assertEquals("BULLET", fields[columnIndex("source")]);
        assertEquals("831", fields[columnIndex("victim_x")]);
        assertEquals("1102", fields[columnIndex("victim_y")]);
    }

    @Test
    void anApiRowCarriesNoVictim() {
        String[] fields = ReachLogger.row("game-1", 100, UnitType.Terran_Marine, 128, 160,
                EnemyReachMemory.Source.API, null).split(",", -1);

        assertEquals("-1", fields[columnIndex("victim_x")]);
        assertEquals("-1", fields[columnIndex("victim_y")]);
    }

    @Test
    void theRegisteredLoggerWritesEveryRiseTheMemoryReports(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(ReachLogger.FILE);
        ReachLogger logger = new ReachLogger(null, "game-1", new TelemetryWriter(file, ReachLogger.HEADER));
        ReachTelemetry.register(logger);
        EnemyReachMemory memory = new EnemyReachMemory();
        Position ling = new Position(848, 1100);

        memory.learnFromBunkerShot(ling, UnitType.Zerg_Zergling, ling,
                Collections.singleton(new Position(848, 896)), 224, 7546);
        memory.recordHurt(ling, 7600);
        logger.onEnd();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(ReachLogger.HEADER, lines.get(0));
        assertEquals(3, lines.size());
        assertEquals("BULLET", lines.get(1).split(",", -1)[columnIndex("source")]);
        assertEquals("HURTMARK", lines.get(2).split(",", -1)[columnIndex("source")]);
        assertEquals("NONE", lines.get(2).split(",", -1)[columnIndex("unit_type")]);
    }
}
