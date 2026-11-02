package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;
import unit.squad.OverlordParking;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OverlordParkLoggerTest {

    @Test
    void anchorRowHasOneFieldPerHeaderColumnOnceTheGameIdIsPrefixed() {
        String row = "g," + OverlordParkLogger.anchorRow(900, 7, new Position(10, 20), null, new Position(30, 40),
                OverlordParking.Reason.ASSIGNED, UnitRole.IDLE);
        assertEquals(OverlordParkLogger.HEADER.split(",").length, row.split(",").length);
        assertEquals("g,900,ANCHOR,7,10,20,-1,-1,30,40,ASSIGNED,IDLE,-1,-1", row);
    }

    @Test
    void anchorRowCarriesThePreviousAnchor() {
        String row = OverlordParkLogger.anchorRow(900, 7, new Position(10, 20), new Position(1, 2),
                new Position(30, 40), OverlordParking.Reason.SPORE_LOST, UnitRole.RALLY);
        assertEquals("900,ANCHOR,7,10,20,1,2,30,40,SPORE_LOST,RALLY,-1,-1", row);
    }

    @Test
    void diedRowHasOneFieldPerHeaderColumnOnceTheGameIdIsPrefixed() {
        String row = "g," + OverlordParkLogger.diedRow(1200, 7, new Position(10, 20), UnitRole.IDLE, 96.5, true);
        assertEquals(OverlordParkLogger.HEADER.split(",").length, row.split(",").length);
        assertEquals("g,1200,DIED,7,10,20,-1,-1,-1,-1,NONE,IDLE,96.5000,1", row);
    }

    @Test
    void diedRowWithNoSporeWritesMinusOne() {
        String row = OverlordParkLogger.diedRow(1200, 7, new Position(10, 20), UnitRole.SCOUT, -1, false);
        assertEquals("1200,DIED,7,10,20,-1,-1,-1,-1,NONE,SCOUT,-1,0", row);
    }
}
