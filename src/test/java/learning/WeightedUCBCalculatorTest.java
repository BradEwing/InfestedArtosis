package learning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WeightedUCBCalculatorTest {

    @Test
    void testFindBestStrategySelectsFourPoolWithTomasCereHistory() {
        String history = "NNNNNNNNNNNNNNNNNNNNppOoOOoFHFHFhFhFttFpFhFtFOFnFOOFOFoFhFoFnFTFtFNFNFnFpFoFNFNFn"
                + "FtFHFhFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFnFNFNFNFn";
        Map<String, Record> opponentRecords = new HashMap<>();
        Map<String, MapAwareRecord> mapRecords = new HashMap<>();
        Map<Character, String> openerNames = new HashMap<>();
        openerNames.put('F', "4Pool");
        openerNames.put('N', "9PoolSpeed");
        openerNames.put('P', "12Pool");
        openerNames.put('O', "Overpool");
        openerNames.put('H', "12Hatch");
        openerNames.put('T', "3HatchBeforePool");
        for (Map.Entry<Character, String> opener : openerNames.entrySet()) {
            opponentRecords.put(opener.getValue(), recordFromHistory(opener.getKey(), opener.getValue(), history));
            mapRecords.put(WeightedUCBCalculator.createMapKey("MapA", opener.getValue()),
                    mapRecordFromHistory(opener.getKey(), opener.getValue(), history));
        }
        List<Long> gameTimestamps = new ArrayList<>();
        for (long timestamp = 1; timestamp <= history.length(); timestamp++) {
            gameTimestamps.add(timestamp);
        }
        List<String> candidates = Arrays.asList(
                "12Pool", "3HatchBeforePool", "Overpool", "12Hatch", "4Pool", "9PoolSpeed");

        String selected = WeightedUCBCalculator.findBestStrategy(candidates, "MapA", "Tomas Cere",
                mapRecords, opponentRecords, history.length(), gameTimestamps);

        assertEquals("4Pool", selected);
    }

    private Record recordFromHistory(char code, String opener, String history) {
        Record record = Record.builder().opener(opener).build();
        for (int i = 0; i < history.length(); i++) {
            char result = history.charAt(i);
            if (Character.toUpperCase(result) == code) {
                if (Character.isUpperCase(result)) {
                    record.addWinTimestamp(i + 1L);
                    record.setWins(record.getWins() + 1);
                } else {
                    record.addLossTimestamp(i + 1L);
                    record.setLosses(record.getLosses() + 1);
                }
            }
        }
        return record;
    }

    private MapAwareRecord mapRecordFromHistory(char code, String strategy, String history) {
        MapAwareRecord record = MapAwareRecord.builder().strategy(strategy).mapName("MapA").build();
        for (int i = 0; i < history.length(); i++) {
            char result = history.charAt(i);
            if (Character.toUpperCase(result) == code) {
                if (Character.isUpperCase(result)) {
                    record.addWinTimestamp(i + 1L);
                    record.setWins(record.getWins() + 1);
                } else {
                    record.addLossTimestamp(i + 1L);
                    record.setLosses(record.getLosses() + 1);
                }
            }
        }
        return record;
    }
}
