package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementTest {

    private static final Position ANCHOR = new Position(1000, 1000);
    private static final String GAME_ID = "game-1";

    private static Engagement engagementAt(int frame) {
        return new Engagement(7, frame, ANCHOR);
    }

    private static String[] engagementFields(Engagement engagement) {
        return engagement.toRow(GAME_ID).split(",", -1);
    }

    @Test
    void engagementRowMatchesTheEngagementHeader() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");
        engagement.noteSupply(1, 12, 8);

        assertEquals(TelemetryLog.ENGAGEMENT_HEADER.split(",", -1).length, engagementFields(engagement).length);
    }

    @Test
    void unitRowMatchesTheUnitHeader() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Hydralisk, 80, "FIGHT", "squad-a");

        List<String> rows = engagement.toUnitRows(GAME_ID);
        assertEquals(1, rows.size());
        assertEquals(TelemetryLog.ENGAGEMENT_UNIT_HEADER.split(",", -1).length, rows.get(0).split(",", -1).length);
    }

    @Test
    void arrivalOffsetIsRelativeToTheEngagementStart() {
        Engagement engagement = engagementAt(100);
        engagement.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");
        engagement.noteUnit(340, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-b");

        List<String> rows = engagement.toUnitRows(GAME_ID);
        assertEquals("0", rows.get(0).split(",", -1)[6]);
        assertEquals("240", rows.get(1).split(",", -1)[6]);
    }

    @Test
    void supplyWeightedArrivalQuantilesUseHalfSupply() {
        Engagement engagement = engagementAt(0);
        engagement.noteUnit(0, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");
        engagement.noteUnit(0, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");
        engagement.noteUnit(0, 3, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");
        engagement.noteUnit(480, 4, UnitType.Zerg_Ultralisk, 400, "FIGHT", "squad-b");

        String[] fields = engagementFields(engagement);
        assertEquals("0", fields[12]);
        assertEquals("480", fields[13]);
        assertEquals("480", fields[14]);
    }

    @Test
    void deathsAreCountedOnceAndInHalfSupply() {
        Engagement engagement = engagementAt(0);
        engagement.noteUnit(0, 1, UnitType.Zerg_Hydralisk, 80, "FIGHT", "squad-a");
        engagement.noteUnit(0, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");

        engagement.recordDeath(1, 24, new Position(500, 600));
        engagement.recordDeath(1, 32, new Position(500, 600));
        engagement.recordDeath(99, 40, new Position(500, 600));

        assertEquals(1, engagement.getUnitsLost());
        assertEquals(UnitType.Zerg_Hydralisk.supplyRequired(), engagement.getSupplyLost());

        String[] deadUnit = engagement.toUnitRows(GAME_ID).get(0).split(",", -1);
        assertEquals("1", deadUnit[8]);
        assertEquals("24", deadUnit[9]);
        assertEquals("500", deadUnit[10]);
        assertEquals("600", deadUnit[11]);
    }

    @Test
    void statusChangesCountTransitionsNotSamples() {
        Engagement engagement = engagementAt(0);
        engagement.noteStatus("FIGHT");
        engagement.noteStatus("FIGHT");
        engagement.noteStatus("RETREAT");
        engagement.noteStatus("RETREAT");
        engagement.noteStatus("FIGHT");

        String[] fields = engagementFields(engagement);
        assertEquals("FIGHT", fields[20]);
        assertEquals("FIGHT", fields[21]);
        assertEquals("2", fields[22]);
    }

    @Test
    void absorbKeepsTheOlderStartAndSumsLosses() {
        Engagement first = engagementAt(100);
        first.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");
        first.noteSupply(1, 10, 4);
        first.recordDeath(1, 110, new Position(1, 1));

        Engagement second = new Engagement(8, 200, new Position(1200, 1000));
        second.noteUnit(200, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-b");
        second.noteSupply(1, 10, 6);
        second.recordDeath(2, 210, new Position(2, 2));

        first.absorb(second);

        assertEquals(100, first.getStartFrame());
        assertEquals(2, first.getUnitsLost());
        assertEquals(2, first.getSupplyLost());

        String[] fields = engagementFields(first);
        assertEquals("2", fields[19]);
        assertEquals("6", fields[15]);
        assertEquals("100", fields[2]);
    }

    @Test
    void absorbedUnitsKeepTheirAbsoluteArrivalAndGetRebasedOffsets() {
        Engagement first = engagementAt(100);
        first.noteUnit(100, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");

        Engagement second = new Engagement(8, 300, new Position(1200, 1000));
        second.noteUnit(300, 2, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-b");

        first.absorb(second);

        List<String> rows = first.toUnitRows(GAME_ID);
        assertEquals(2, rows.size());
        assertEquals("300", rows.get(1).split(",", -1)[5]);
        assertEquals("200", rows.get(1).split(",", -1)[6]);
    }

    @Test
    void noRowFieldContainsAComma() {
        Engagement engagement = engagementAt(0);
        engagement.noteUnit(0, 1, UnitType.Zerg_Zergling, 35, "FIGHT", "squad-a");
        engagement.noteSupply(1, 10, 4);
        engagement.noteStatus("FIGHT");
        engagement.noteSim(1.25, 1.4, "RETREAT");

        assertEquals(TelemetryLog.ENGAGEMENT_HEADER.split(",", -1).length, engagementFields(engagement).length);
        for (String field : engagementFields(engagement)) {
            assertTrue(field.indexOf(',') < 0);
        }
    }
}
