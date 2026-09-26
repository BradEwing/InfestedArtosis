package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.StaticDefenseZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedFireLoggerTest {

    private static final StaticDefenseZone TANK_ZONE = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode,
            new Position(2461, 373), 400);

    private static int columnIndex(String column) {
        String[] columns = FixedFireLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void aLurkerHoldRowCarriesTheHoldPointAndTheZone() {
        String[] fields = FixedFireLogger.row("game-1", 9100, FixedFireLogger.EVENT_LURKER_HOLD,
                FixedFireLogger.unitCells(166, UnitType.Zerg_Lurker, new Position(2501, 644)),
                new Position(2520, 900), TANK_ZONE, FixedFireLogger.targetCells(-1, null), "HIT").split(",", -1);

        assertEquals(FixedFireLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("LURKER_HOLD", fields[columnIndex("event")]);
        assertEquals("166", fields[columnIndex("unit_id")]);
        assertEquals("Zerg_Lurker", fields[columnIndex("unit_type")]);
        assertEquals("2501", fields[columnIndex("unit_x")]);
        assertEquals("2520", fields[columnIndex("point_x")]);
        assertEquals("900", fields[columnIndex("point_y")]);
        assertEquals("Terran_Siege_Tank_Siege_Mode", fields[columnIndex("zone_type")]);
        assertEquals("2461", fields[columnIndex("zone_x")]);
        assertEquals("400", fields[columnIndex("zone_reach")]);
        assertEquals("-1", fields[columnIndex("target_id")]);
        assertEquals("NONE", fields[columnIndex("target_type")]);
        assertEquals("HIT", fields[columnIndex("reason")]);
    }

    @Test
    void aSkipRowNamesTheTargetAndWhereItStood() {
        String[] fields = FixedFireLogger.row("game-1", 9200, FixedFireLogger.EVENT_COOLDOWN_SKIP,
                FixedFireLogger.unitCells(166, UnitType.Zerg_Lurker, new Position(2501, 900)),
                new Position(2461, 373), TANK_ZONE,
                FixedFireLogger.targetCells(55, UnitType.Terran_Siege_Tank_Siege_Mode), null).split(",", -1);

        assertEquals(FixedFireLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("COOLDOWN_SKIP", fields[columnIndex("event")]);
        assertEquals("55", fields[columnIndex("target_id")]);
        assertEquals("Terran_Siege_Tank_Siege_Mode", fields[columnIndex("target_type")]);
        assertEquals("373", fields[columnIndex("point_y")]);
        assertEquals("NONE", fields[columnIndex("reason")]);
    }

    @Test
    void aReleaseRowHasNoZone() {
        String[] fields = FixedFireLogger.row("game-1", 9300, FixedFireLogger.EVENT_LURKER_HOLD_RELEASE,
                FixedFireLogger.unitCells(166, UnitType.Zerg_Lurker, new Position(2520, 900)),
                new Position(2520, 900), null, FixedFireLogger.targetCells(-1, null), "COMMIT").split(",", -1);

        assertEquals("NONE", fields[columnIndex("zone_type")]);
        assertEquals("-1", fields[columnIndex("zone_x")]);
        assertEquals("-1", fields[columnIndex("zone_reach")]);
        assertEquals("COMMIT", fields[columnIndex("reason")]);
    }
}
