package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StagingLoggerTest {

    private static int columns(String csv) {
        return csv.split(",", -1).length;
    }

    @Test
    void anEnterRowHasOneCellPerHeaderColumn() {
        StagingRow reading = new StagingRow("squad-1", true, 12, 11, 0.4, 480, 224, 0);

        String row = StagingLogger.row("game", 3000, StagingLogger.EVENT_ENTER, "NONE", reading);

        assertEquals(columns(StagingLogger.HEADER), columns(row));
        assertEquals("game,3000,STANDOFF_ENTER,squad-1,NONE,true,12,11,0.4000,480,224,0", row);
    }

    @Test
    void anExitRowCarriesItsReason() {
        StagingRow reading = new StagingRow("squad-1", false, 12, 11, 0.8, 300, 224, 96);

        String row = StagingLogger.row("game", 3096, StagingLogger.EVENT_EXIT, "GATHERED", reading);

        assertEquals(columns(StagingLogger.HEADER), columns(row));
        assertEquals("game,3096,STANDOFF_EXIT,squad-1,GATHERED,false,12,11,0.8000,300,224,96", row);
    }

    @Test
    void aReinforcementRowLeavesTheEpisodeColumnsUnevaluated() {
        StagingRow reading = new StagingRow("squad-2", true, 4, 4, -1, -1, -1, -1);

        String row = StagingLogger.row("game", 4594, StagingLogger.EVENT_REINFORCE, "NONE", reading);

        assertEquals(columns(StagingLogger.HEADER), columns(row));
        assertEquals("game,4594,REINFORCE_REAR,squad-2,NONE,true,4,4,-1.0000,-1,-1,-1", row);
    }
}
