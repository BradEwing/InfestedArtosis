package telemetry;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StagingLoggerTest {

    private static int columns(String csv) {
        return csv.split(",", -1).length;
    }

    @Test
    void anEnterRowHasOneCellPerHeaderColumn() {
        StagingRow reading = StagingRow.builder().squadId("squad-1").applied(true).supply(12).members(11)
                .gatheredShare(0.4).enemyDistance(480).standoffDistance(224).framesStaged(0)
                .enemyType("Zerg_Zergling").enemyReach(128).episodeId(3).memberUnitIds("41;42").build();

        String row = StagingLogger.row("game", 3000, StagingLogger.EVENT_ENTER, "NONE", reading);

        assertEquals(columns(StagingLogger.HEADER), columns(row));
        assertEquals("game,3000,STANDOFF_ENTER,squad-1,NONE,true,12,11,0.4000,480,224,0,Zerg_Zergling,128,3,41;42,"
                + "NONE,-1", row);
    }

    @Test
    void anExitRowCarriesItsReason() {
        StagingRow reading = StagingRow.builder().squadId("squad-1").applied(false).supply(12).members(11)
                .gatheredShare(0.8).enemyDistance(300).standoffDistance(224).framesStaged(96)
                .enemyType("Terran_Marine").enemyReach(224).episodeId(3).build();

        String row = StagingLogger.row("game", 3096, StagingLogger.EVENT_EXIT, "GATHERED", reading);

        assertEquals(columns(StagingLogger.HEADER), columns(row));
        assertEquals("game,3096,STANDOFF_EXIT,squad-1,GATHERED,false,12,11,0.8000,300,224,96,Terran_Marine,224,3,"
                + "NONE,NONE,-1", row);
    }

    @Test
    void aReinforcementRowLeavesTheEpisodeColumnsUnevaluated() {
        StagingRow reading = StagingRow.builder().squadId("squad-2").applied(true).supply(4).members(4)
                .episodeId(3).memberUnitIds("7;8;9;10").targetSquadId("squad-1").build();

        String row = StagingLogger.row("game", 4594, StagingLogger.EVENT_REINFORCE, "NONE", reading);

        assertEquals(columns(StagingLogger.HEADER), columns(row));
        assertEquals("game,4594,REINFORCE_REAR,squad-2,NONE,true,4,4,-1.0000,-1,-1,-1,NONE,-1,3,7;8;9;10,squad-1,-1",
                row);
    }

    @Test
    void aLaunchRowCarriesTheMinimumMass() {
        StagingRow reading = StagingRow.builder().squadId("squad-3").applied(true).supply(4).members(4)
                .memberUnitIds("7;8;9;10").minMass(6).build();

        String row = StagingLogger.row("game", 9000, StagingLogger.EVENT_LAUNCH, "EXEMPT", reading);

        assertEquals(columns(StagingLogger.HEADER), columns(row));
        assertEquals("game,9000,LAUNCH,squad-3,EXEMPT,true,4,4,-1.0000,-1,-1,-1,NONE,-1,-1,7;8;9;10,NONE,6", row);
    }

    @Test
    void anEnterRowWritesTheSupplyAndMembersItIsGiven() {
        StagingRow reading = StagingRow.builder().squadId("squad-4").applied(true).supply(6).members(6)
                .episodeId(0).build();

        String[] cells = StagingLogger.row("game", 100, StagingLogger.EVENT_ENTER, "MERGED", reading).split(",", -1);
        List<String> header = Arrays.asList(StagingLogger.HEADER.split(","));

        assertEquals("6", cells[header.indexOf("squad_supply")]);
        assertEquals("6", cells[header.indexOf("members")]);
    }
}
