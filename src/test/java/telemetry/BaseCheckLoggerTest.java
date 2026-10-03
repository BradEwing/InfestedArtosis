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
                new BaseCheckEnd(6400, BaseCheckScheduler.Release.SEEN, false, BaseCheckEnd.SURVIVED));
        assertEquals(BaseCheckLogger.HEADER.split(",").length, row.split(",").length);
        assertEquals("g,7,Zerg_Zergling,10,20,2000,6000,6400,SEEN,0,-1", row);
    }

    @Test
    void aBaseNeverSeenIsWrittenWithAgeMinusOne() {
        String row = BaseCheckLogger.row(7, UnitType.Zerg_Overlord, new TilePosition(10, 20), Integer.MAX_VALUE,
                6000, new BaseCheckEnd(6400, BaseCheckScheduler.Release.THREAT, true, BaseCheckEnd.SURVIVED));
        assertEquals("7,Zerg_Overlord,10,20,-1,6000,6400,THREAT,1,-1", row);
    }

    @Test
    void aRecalledScoutThatDiedIsLostWithItsDeathFrameAfterTheRecall() {
        String row = BaseCheckLogger.row(7, UnitType.Zerg_Zergling, new TilePosition(10, 20), 2000, 6000,
                new BaseCheckEnd(6400, BaseCheckScheduler.Release.LOST, false, 6413));
        assertEquals("7,Zerg_Zergling,10,20,2000,6000,6400,LOST,0,6413", row);
    }
}
