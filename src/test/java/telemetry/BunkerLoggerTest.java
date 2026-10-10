package telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BunkerLoggerTest {

    @AfterEach
    void clearSink() {
        BunkerTelemetry.clear();
    }

    private static int columnIndex(String column) {
        String[] columns = BunkerLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] fields(String row) {
        String[] fields = row.split(",", -1);
        assertEquals(BunkerLogger.COLUMNS.length, fields.length);
        return fields;
    }

    @Test
    void anEconRowCarriesTheStanceAndTheDronesPlannedAndMade() {
        String[] fields = fields(BunkerLogger.econRow("game-1",
                new BunkerStanceEvent(9000, "STANCE_END", "ATTACKING", 2, 20, 18, 4, 3)));

        assertEquals("game-1", fields[columnIndex("game_id")]);
        assertEquals("9000", fields[columnIndex("frame")]);
        assertEquals("BUNKER_ECON", fields[columnIndex("row_type")]);
        assertEquals("STANCE_END", fields[columnIndex("event")]);
        assertEquals("ATTACKING", fields[columnIndex("reason")]);
        assertEquals("2", fields[columnIndex("stance_id")]);
        assertEquals("20", fields[columnIndex("drones")]);
        assertEquals("18", fields[columnIndex("workers")]);
        assertEquals("4", fields[columnIndex("extra_planned")]);
        assertEquals("3", fields[columnIndex("extra_made")]);
    }

    @Test
    void anEconRowLeavesTheColumnsOfOtherRowTypesEmpty() {
        String[] fields = fields(BunkerLogger.econRow("game-1",
                new BunkerStanceEvent(9000, "ROUND_OPEN", "BUNKER_STANCE", 1, 14, 14, 2, 0)));

        for (String column : new String[] {"squad_id", "own_strength", "bunker_price", "ratio", "release_ratio",
            "ling_count", "bunker_id", "bunker_x", "bunker_y", "bunker_hp", "engagement_id", "start_frame",
            "end_frame", "our_lost", "lings_lost", "enemy_lost", "hp_start", "hp_end", "broken", "squad_size",
            "squad_x", "squad_y", "entry", "units_lost"}) {
            assertEquals("", fields[columnIndex(column)], column);
        }
    }

    @Test
    void aStanceEventIsWrittenAsAnEconRowUnderTheHeader(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("telemetry_bunker.csv");
        TelemetryWriter writer = new TelemetryWriter(file, BunkerLogger.HEADER);
        BunkerLogger logger = new BunkerLogger(null, null, "game-1", writer);

        logger.onStance(new BunkerStanceEvent(6000, "STANCE_START", "ACTIVE", 1, 14, 14, 0, 0));
        writer.flush();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(BunkerLogger.HEADER, lines.get(0));
        assertEquals(2, lines.size());
        assertEquals("BUNKER_ECON", lines.get(1).split(",", -1)[columnIndex("row_type")]);
    }
}
