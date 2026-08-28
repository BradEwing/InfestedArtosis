package telemetry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryLogTest {

    @Test
    void disabledLogWritesNothing(@TempDir Path directory) throws IOException {
        TelemetryLog log = new TelemetryLog(false, directory.resolve("write"));

        log.appendGame("game-row");
        log.appendEngagement("engagement-row");
        log.appendEngagementUnit("unit-row");
        log.flush();

        assertFalse(Files.exists(directory.resolve("write")));
        try (Stream<Path> entries = Files.list(directory)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void enabledLogWritesHeaderOnceThenAppends(@TempDir Path directory) throws IOException {
        Path writeDirectory = directory.resolve("bwapi-data").resolve("write");
        TelemetryLog log = new TelemetryLog(true, writeDirectory);

        log.appendEngagement("first");
        log.flush();
        log.appendEngagement("second");
        log.flush();

        Path file = writeDirectory.resolve(TelemetryLog.ENGAGEMENT_FILE);
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertEquals(TelemetryLog.ENGAGEMENT_HEADER, lines.get(0));
        assertEquals("first", lines.get(1));
        assertEquals("second", lines.get(2));
    }

    @Test
    void enabledLogOnlyCreatesFilesThatReceivedRows(@TempDir Path directory) {
        TelemetryLog log = new TelemetryLog(true, directory);

        log.appendGame("game-row");
        log.flush();

        assertTrue(Files.exists(directory.resolve(TelemetryLog.GAME_FILE)));
        assertFalse(Files.exists(directory.resolve(TelemetryLog.ENGAGEMENT_FILE)));
        assertFalse(Files.exists(directory.resolve(TelemetryLog.ENGAGEMENT_UNIT_FILE)));
    }

    @Test
    void everyFileNameCarriesTheTelemetryPrefix() {
        assertTrue(TelemetryLog.GAME_FILE.startsWith("telemetry_"));
        assertTrue(TelemetryLog.ENGAGEMENT_FILE.startsWith("telemetry_"));
        assertTrue(TelemetryLog.ENGAGEMENT_UNIT_FILE.startsWith("telemetry_"));
    }

    @Test
    void writerDisablesItselfAfterAFailedFlush(@TempDir Path directory) throws IOException {
        Path collision = directory.resolve("occupied");
        Files.createFile(collision);
        TelemetryWriter writer = new TelemetryWriter(collision.resolve("telemetry_combat_game.csv"), "header");

        writer.append("row");
        writer.flush();

        assertTrue(writer.isDisabled());
        writer.append("another");
        writer.flush();
        assertTrue(writer.isDisabled());
    }
}
