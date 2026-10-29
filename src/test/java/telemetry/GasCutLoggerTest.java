package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GasCutLoggerTest {

    @Test
    void aRowHasOneCellPerHeaderColumn() {
        String row = GasCutLogger.row("game-1", 5000, "DEMAND", true, "UPGRADE", 150, 12, 0);
        assertEquals(GasCutLogger.HEADER.split(",", -1).length, row.split(",", -1).length);
        assertEquals("game-1,5000,DEMAND,1,UPGRADE,150,12,0", row);
    }

    @Test
    void aCutRowNamesNoPlan() {
        assertEquals("game-1,4000,CUT,0,NONE,0,40,3", GasCutLogger.row("game-1", 4000, "CUT", false, null, 0, 40, 3));
    }
}
