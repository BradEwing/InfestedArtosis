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
        String[] fields = ("game-1," + BurrowLogger.row(BurrowCommand.builder().frame(15703).unitId(208)
                .reason(BurrowReason.UNDER_FIRE_WITHDRAW).role("CONTAIN").position(new Position(900, 3413))
                .hitPoints(82).containPoint(new Position(910, 3400)).withdrawPoint(new Position(700, 3300))
                .withdrawZones(0).build())).split(",", -1);

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
        assertEquals("700", fields[columnIndex("withdraw_x")]);
        assertEquals("3300", fields[columnIndex("withdraw_y")]);
        assertEquals("0", fields[columnIndex("withdraw_zones")]);
    }

    @Test
    void aRefusedBurrowWritesItsReasonAndNoWithdrawColumns() {
        String[] fields = ("game-1," + BurrowLogger.row(BurrowCommand.builder().frame(900).unitId(5).burrow(true)
                .reason(BurrowReason.BURROW_REFUSED_UNDER_FIRE).role("CONTAIN").position(new Position(10, 20))
                .hitPoints(90).containPoint(new Position(30, 40)).build())).split(",", -1);

        assertEquals(BurrowLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("BURROW", fields[columnIndex("command")]);
        assertEquals("BURROW_REFUSED_UNDER_FIRE", fields[columnIndex("reason")]);
        assertEquals("-1", fields[columnIndex("withdraw_x")]);
        assertEquals("-1", fields[columnIndex("withdraw_zones")]);
    }

    @Test
    void theWithdrawColumnsAreAppendedAfterTheContainPoint() {
        assertEquals(columnIndex("contain_y") + 1, columnIndex("withdraw_x"));
        assertEquals(BurrowLogger.HEADER.split(",", -1).length - 1, columnIndex("withdraw_zones"));
    }

    @Test
    void aLurkerWithNoContainPointWritesMinusOne() {
        String[] fields = ("game-1," + BurrowLogger.row(BurrowCommand.builder().frame(100).unitId(7).burrow(true)
                .reason(BurrowReason.FIGHT_ENEMY_IN_RANGE).role("FIGHT").position(new Position(10, 20))
                .hitPoints(125).build())).split(",", -1);

        assertEquals("BURROW", fields[columnIndex("command")]);
        assertEquals("-1", fields[columnIndex("contain_x")]);
        assertEquals("-1", fields[columnIndex("contain_y")]);
        assertEquals("-1", fields[columnIndex("withdraw_x")]);
        assertEquals("-1", fields[columnIndex("withdraw_y")]);
        assertEquals("-1", fields[columnIndex("withdraw_zones")]);
    }

    @Test
    void theRegisteredLoggerWritesEveryCommand(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(BurrowLogger.FILE);
        BurrowLogger logger = new BurrowLogger(null, "game-1", new TelemetryWriter(file, BurrowLogger.HEADER));
        BurrowTelemetry.register(logger);

        BurrowTelemetry.burrowCommand(BurrowCommand.builder().frame(10).unitId(1).burrow(true)
                .reason(BurrowReason.CONTAIN_HOLD).role("CONTAIN").position(new Position(1, 2)).hitPoints(125)
                .containPoint(new Position(3, 4)).build());
        BurrowTelemetry.burrowCommand(BurrowCommand.builder().frame(20).unitId(1)
                .reason(BurrowReason.CONTAIN_POINT_MOVED).role("CONTAIN").position(new Position(1, 2))
                .hitPoints(125).containPoint(new Position(90, 4)).build());
        logger.onEnd();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(BurrowLogger.HEADER, lines.get(0));
        assertEquals(3, lines.size());
        assertEquals("CONTAIN_HOLD", lines.get(1).split(",", -1)[columnIndex("reason")]);
        assertEquals("CONTAIN_POINT_MOVED", lines.get(2).split(",", -1)[columnIndex("reason")]);
    }
}
