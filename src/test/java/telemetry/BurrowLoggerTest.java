package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BurrowLoggerTest {

    @AfterEach
    void clearSink() {
        BurrowTelemetry.clear();
    }

    private static int columnIndex(String column) {
        String[] columns = BurrowLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void anUnburrowRowCarriesTheReasonAndTheContainPoint() {
        String[] fields = ("game-1," + BurrowLogger.row(15703, 208, false, BurrowReason.UNDER_FIRE_WITHDRAW, "CONTAIN",
                new Position(900, 3413), 82, new Position(910, 3400))).split(",", -1);

        assertEquals(BurrowLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("15703", fields[columnIndex("frame")]);
        assertEquals("208", fields[columnIndex("unit_id")]);
        assertEquals("UNBURROW", fields[columnIndex("command")]);
        assertEquals("UNDER_FIRE_WITHDRAW", fields[columnIndex("reason")]);
        assertEquals("CONTAIN", fields[columnIndex("role")]);
        assertEquals("900", fields[columnIndex("x")]);
        assertEquals("82", fields[columnIndex("hit_points")]);
        assertEquals("910", fields[columnIndex("contain_x")]);
        assertEquals("3400", fields[columnIndex("contain_y")]);
    }

    @Test
    void aLurkerWithNoContainPointWritesMinusOne() {
        String[] fields = ("game-1," + BurrowLogger.row(100, 7, true, BurrowReason.FIGHT_ENEMY_IN_RANGE, "FIGHT",
                new Position(10, 20), 125, null)).split(",", -1);

        assertEquals("BURROW", fields[columnIndex("command")]);
        assertEquals("-1", fields[columnIndex("contain_x")]);
        assertEquals("-1", fields[columnIndex("contain_y")]);
    }

    @Test
    void theRegisteredLoggerWritesEveryCommand(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(BurrowLogger.FILE);
        BurrowLogger logger = new BurrowLogger(null, "game-1", new TelemetryWriter(file, BurrowLogger.HEADER));
        BurrowTelemetry.register(logger);

        BurrowTelemetry.burrowCommand(10, 1, true, BurrowReason.CONTAIN_HOLD, "CONTAIN", new Position(1, 2), 125,
                new Position(3, 4));
        BurrowTelemetry.burrowCommand(20, 1, false, BurrowReason.CONTAIN_POINT_MOVED, "CONTAIN", new Position(1, 2),
                125, new Position(90, 4));
        logger.onEnd();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(BurrowLogger.HEADER, lines.get(0));
        assertEquals(3, lines.size());
        assertEquals("CONTAIN_HOLD", lines.get(1).split(",", -1)[columnIndex("reason")]);
        assertEquals("CONTAIN_POINT_MOVED", lines.get(2).split(",", -1)[columnIndex("reason")]);
    }
}
