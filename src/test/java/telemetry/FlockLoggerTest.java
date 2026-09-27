package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import unit.squad.SquadStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlockLoggerTest {

    @AfterEach
    void clearSink() {
        FlockTelemetry.clear();
    }

    private static int columnIndex(String column) {
        String[] columns = FlockLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void aSampleRowCarriesTheSpreadInHeaderOrder() {
        FlockRow sample = FlockRow.builder()
                .frame(11450)
                .squadId("squad-1")
                .event(FlockRow.Event.SAMPLE)
                .status(SquadStatus.FIGHT)
                .mutas(5)
                .centroid(new Position(2687, 3246))
                .medianDistance(215.5)
                .maxDistance(897)
                .regrouping(1)
                .build();

        String[] fields = FlockLogger.row("game-1", sample).split(",", -1);

        assertEquals(FlockLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("SAMPLE", fields[columnIndex("event")]);
        assertEquals("FIGHT", fields[columnIndex("status")]);
        assertEquals("2687", fields[columnIndex("centroid_x")]);
        assertEquals("215.5000", fields[columnIndex("median_distance")]);
        assertEquals("897.0000", fields[columnIndex("max_distance")]);
        assertEquals("1", fields[columnIndex("regrouping")]);
        assertEquals("-1", fields[columnIndex("unit_id")]);
        assertEquals("-1.0000", fields[columnIndex("nearest_mate_distance")]);
    }

    @Test
    void aLossWithoutASquadIsWrittenWithNoneAndMinusOnes(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(FlockLogger.FILE);
        FlockLogger logger = new FlockLogger(null, "game-1", new TelemetryWriter(file, FlockLogger.HEADER));
        FlockTelemetry.register(logger);

        FlockTelemetry.row(FlockRow.builder()
                .frame(15603)
                .event(FlockRow.Event.MUTA_LOST)
                .unitId(265)
                .centroid(new Position(2486, 4053))
                .build());
        logger.onEnd();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(FlockLogger.HEADER, lines.get(0));
        String[] fields = lines.get(1).split(",", -1);
        assertEquals("NONE", fields[columnIndex("squad_id")]);
        assertEquals("MUTA_LOST", fields[columnIndex("event")]);
        assertEquals("265", fields[columnIndex("unit_id")]);
        assertEquals("-1", fields[columnIndex("mutas")]);
        assertEquals("-1.0000", fields[columnIndex("nearest_mate_distance")]);
    }
}
