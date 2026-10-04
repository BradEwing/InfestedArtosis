package telemetry;

import bwapi.Position;
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
        assertEquals("g,7,Zerg_Zergling,10,20,2000,6000,6400,SEEN,0,-1,1", row);
    }

    @Test
    void aBaseNeverSeenIsWrittenWithAgeMinusOne() {
        String row = BaseCheckLogger.row(7, UnitType.Zerg_Overlord, new TilePosition(10, 20), Integer.MAX_VALUE,
                6000, new BaseCheckEnd(6400, BaseCheckScheduler.Release.THREAT, true, BaseCheckEnd.SURVIVED));
        assertEquals("7,Zerg_Overlord,10,20,-1,6000,6400,THREAT,1,-1,1", row);
    }

    @Test
    void aRecalledScoutThatDiedIsLostWithItsDeathFrameAfterTheRecall() {
        String row = BaseCheckLogger.row(7, UnitType.Zerg_Zergling, new TilePosition(10, 20), 2000, 6000,
                new BaseCheckEnd(6400, BaseCheckScheduler.Release.LOST, false, 6413));
        assertEquals("7,Zerg_Zergling,10,20,2000,6000,6400,LOST,0,6413,1", row);
    }

    @Test
    void theSecondZerglingOfAPairIsWrittenAsNotPrimary() {
        String row = BaseCheckLogger.row(8, UnitType.Zerg_Zergling, new TilePosition(10, 20), 2000, 6000,
                new BaseCheckEnd(6400, BaseCheckScheduler.Release.LOST, false, 6413, false));
        assertEquals("8,Zerg_Zergling,10,20,2000,6000,6400,LOST,0,6413,0", row);
    }

    @Test
    void aSkipRowHasOneFieldPerSkipHeaderColumnOnceTheGameIdIsPrefixed() {
        String row = "g," + BaseCheckLogger.skipRow(9000, new TilePosition(10, 20), BaseCheckSkip.DEATH_ROUTE,
                new Position(300, 400));
        assertEquals(BaseCheckLogger.SKIP_HEADER.split(",").length, row.split(",").length);
        assertEquals("g,9000,10,20,DEATH_ROUTE,300,400", row);
    }

    @Test
    void aSkipRowNamesEachReason() {
        assertEquals("1,2,3,SHARED_DEFENCE,4,5", BaseCheckLogger.skipRow(1, new TilePosition(2, 3),
                BaseCheckSkip.SHARED_DEFENCE, new Position(4, 5)));
        assertEquals("1,2,3,DEATH_RECALL,4,5", BaseCheckLogger.skipRow(1, new TilePosition(2, 3),
                BaseCheckSkip.DEATH_RECALL, new Position(4, 5)));
    }
}
