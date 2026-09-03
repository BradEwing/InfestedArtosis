package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningRecordAccumulatorTest {
    @Test
    void reconstructsOpponentAndMapRecordsFromOneHistoryPass() {
        GameRecord openerOnly = game(10L, true, "4Pool", "4Pool", "MapA");
        GameRecord transitioned = game(20L, false, "12Pool", "2HatchMuta", "MapB");
        LearningHistory history = new LearningHistory(Arrays.asList(openerOnly, transitioned));

        OpponentRecord record = new LearningRecordAccumulator("Opponent", Race.Terran).reconstruct(history);

        assertEquals(1, record.getWins());
        assertEquals(1, record.getLosses());
        assertEquals(Arrays.asList(10L, 20L), record.getGameTimestamps());
        assertEquals(1, record.getOpenerRecord().get("4Pool").getWins());
        assertEquals(1, record.getOpenerRecord().get("12Pool").getLosses());
        assertEquals(1, record.getBuildOrderRecord().get("2HatchMuta").getLosses());
        assertTrue(record.getMapSpecificOpenerRecord().containsKey("MapA_4Pool"));
        assertTrue(record.getMapSpecificBuildOrderRecord().containsKey("MapB_2HatchMuta"));
        assertFalse(record.getBuildOrderRecord().containsKey("4Pool"));
    }

    @Test
    void creditsEachDistinctBuildOrderInAChainOnce() {
        GameRecord chained = game(30L, true, "12Pool", "2HatchMuta;3HatchLurker", "MapC");

        OpponentRecord record = new LearningRecordAccumulator("Opponent", Race.Terran)
                .reconstruct(new LearningHistory(Arrays.asList(chained)));

        assertEquals(1, record.getBuildOrderRecord().get("2HatchMuta").getWins());
        assertEquals(1, record.getBuildOrderRecord().get("3HatchLurker").getWins());
        assertTrue(record.getMapSpecificBuildOrderRecord().containsKey("MapC_2HatchMuta"));
        assertTrue(record.getMapSpecificBuildOrderRecord().containsKey("MapC_3HatchLurker"));
    }

    @Test
    void chainValuesEqualToOpenerAndDuplicatesAreSkipped() {
        GameRecord chained = game(40L, false, "Overpool", "Overpool;2HatchMuta;2HatchMuta; 2HatchMuta", "MapD");

        OpponentRecord record = new LearningRecordAccumulator("Opponent", Race.Terran)
                .reconstruct(new LearningHistory(Arrays.asList(chained)));

        assertFalse(record.getBuildOrderRecord().containsKey("Overpool"));
        assertEquals(1, record.getBuildOrderRecord().get("2HatchMuta").getLosses());
    }

    @Test
    void blankChainSegmentsAreIgnored() {
        GameRecord ragged = game(50L, true, "4Pool", ";2HatchMuta;", "MapE");

        OpponentRecord record = new LearningRecordAccumulator("Opponent", Race.Terran)
                .reconstruct(new LearningHistory(Arrays.asList(ragged)));

        assertEquals(1, record.getBuildOrderRecord().get("2HatchMuta").getWins());
        assertFalse(record.getBuildOrderRecord().containsKey(""));
    }

    private GameRecord game(long timestamp, boolean winner, String opener, String buildOrder, String mapName) {
        return GameRecord.builder()
                .timestamp(timestamp)
                .isWinner(winner)
                .opener(opener)
                .buildOrder(buildOrder)
                .mapName(mapName)
                .build();
    }
}
