package telemetry;

import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.scout.BaseCheckScheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseCheckLoggerTest {

    @Test
    void rowHasOneFieldPerHeaderColumnOnceTheGameIdIsPrefixed() {
        String row = "g," + BaseCheckLogger.row(7, UnitType.Zerg_Zergling, new TilePosition(10, 20), 2000, 6000,
                6400, BaseCheckScheduler.Release.SEEN, false);
        assertEquals(BaseCheckLogger.HEADER.split(",").length, row.split(",").length);
        assertEquals("g,7,Zerg_Zergling,10,20,2000,6000,6400,SEEN,0", row);
    }

    @Test
    void aBaseNeverSeenIsWrittenWithAgeMinusOne() {
        String row = BaseCheckLogger.row(7, UnitType.Zerg_Overlord, new TilePosition(10, 20), Integer.MAX_VALUE,
                6000, 6400, BaseCheckScheduler.Release.THREAT, true);
        assertEquals("7,Zerg_Overlord,10,20,-1,6000,6400,THREAT,1", row);
    }
}
