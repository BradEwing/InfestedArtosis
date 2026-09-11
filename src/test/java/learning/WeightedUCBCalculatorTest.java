package learning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WeightedUCBCalculatorTest {

    @Test
    void unplayedCandidatesTieBreakTheSameWhateverOrderTheyArriveIn() {
        Map<String, Record> noRecords = new HashMap<>();
        Map<String, MapAwareRecord> noMapRecords = new HashMap<>();
        List<Long> noTimestamps = new ArrayList<>();

        String forward = WeightedUCBCalculator.findBestStrategy(
                Arrays.asList("SpeedlingAllIn", "1HatchSpire", "3HatchMuta"),
                "(4)Python.scx", noMapRecords, noRecords, 7, noTimestamps);
        String reversed = WeightedUCBCalculator.findBestStrategy(
                Arrays.asList("3HatchMuta", "1HatchSpire", "SpeedlingAllIn"),
                "(4)Python.scx", noMapRecords, noRecords, 7, noTimestamps);

        assertEquals(forward, reversed);
    }

    @Test
    void theTieBreakIsAStableTotalOrder() {
        assertTrue(WeightedUCBCalculator.winsTieBreak("Overpool", "12Hatch", "(4)Python.scx", 3)
                != WeightedUCBCalculator.winsTieBreak("12Hatch", "Overpool", "(4)Python.scx", 3));
        assertEquals(
                WeightedUCBCalculator.winsTieBreak("Overpool", "12Hatch", "(4)Python.scx", 3),
                WeightedUCBCalculator.winsTieBreak("Overpool", "12Hatch", "(4)Python.scx", 3));
    }

    @Test
    void everyCandidateTakesSomeOfTheTiesOverAColdStart() {
        List<String> openers = Arrays.asList(
                "12Hatch", "12Pool", "3HatchBeforePool", "4Pool", "9PoolSpeed", "Overpool");
        List<String> maps = Arrays.asList(
                "(4)Python.scx", "(3)TauCross.scx", "(2)Destination.scx", "(4)Andromeda.scx",
                "(4)FightingSpirit.scx", "(2)Benzene.scx", "(4)Jade.scx", "(4)Icarus.scm");

        Map<String, Integer> wins = new HashMap<>();
        for (int game = 0; game < 60; game++) {
            String map = maps.get(game % maps.size());
            String best = openers.get(0);
            for (String candidate : openers) {
                if (WeightedUCBCalculator.winsTieBreak(candidate, best, map, game)) {
                    best = candidate;
                }
            }
            wins.merge(best, 1, Integer::sum);
        }

        assertEquals(openers.size(), wins.size(), "some opener never took a tie: " + wins);
    }

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

        String selected = WeightedUCBCalculator.findBestStrategy(candidates, "MapA",
                mapRecords, opponentRecords, history.length(), gameTimestamps);

        assertEquals("4Pool", selected);
    }

    /**
     * The same fixture history picks the same opener whether it is the opponent's 53rd game or
     * its 1324th. The old ln(totalGames) term grew the score with lifetime, so a long-played
     * opponent explored hardest of all.
     */
    @Test
    void theSelectionDoesNotDependOnHowLongTheOpponentHasBeenPlayed() {
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

        String atFiftyThree = WeightedUCBCalculator.findBestStrategy(candidates, "MapA",
                mapRecords, opponentRecords, 53, gameTimestamps);
        String atThreeThirtyNine = WeightedUCBCalculator.findBestStrategy(candidates, "MapA",
                mapRecords, opponentRecords, 339, gameTimestamps);
        String atThirteenTwentyFour = WeightedUCBCalculator.findBestStrategy(candidates, "MapA",
                mapRecords, opponentRecords, 1324, gameTimestamps);

        assertEquals(atFiftyThree, atThreeThirtyNine, "The pick should not move with lifetime length");
        assertEquals(atFiftyThree, atThirteenTwentyFour, "The pick should not move with lifetime length");
    }

    /**
     * A strategy the opponent record has never played is chosen before a winning incumbent, and
     * the untried strategies take their first exposures one after another.
     */
    @Test
    void anUnplayedStrategyIsChosenBeforeAWinningIncumbent() {
        Map<String, Record> opponentRecords = new HashMap<>();
        Map<String, MapAwareRecord> mapRecords = new HashMap<>();
        List<Long> gameTimestamps = new ArrayList<>();
        opponentRecords.put("4Pool", thirtyPercentIncumbent(gameTimestamps));
        opponentRecords.put("Overpool", Record.builder().opener("Overpool").build());
        List<String> candidates = Arrays.asList("12Pool", "Overpool", "4Pool", "9PoolSpeed");

        List<String> firstExposures = new ArrayList<>();
        for (int game = 0; game < 3; game++) {
            String selected = WeightedUCBCalculator.findBestStrategy(candidates, "MapA",
                    mapRecords, opponentRecords, gameTimestamps.size(), gameTimestamps);
            firstExposures.add(selected);
            long timestamp = gameTimestamps.size() + 1L;
            gameTimestamps.add(timestamp);
            Record record = opponentRecords.computeIfAbsent(selected, name -> Record.builder().opener(name).build());
            record.addLossTimestamp(timestamp);
            record.setLosses(record.getLosses() + 1);
        }

        assertEquals(new HashSet<>(Arrays.asList("12Pool", "Overpool", "9PoolSpeed")), new HashSet<>(firstExposures),
                "every untried strategy must be chosen before the incumbent, measured " + firstExposures);
    }

    /**
     * Once every strategy has been played, a thin arm earns at most the curiosity cap, so it
     * cannot claim the slot from a strategy whose discounted win rate leads by more than that.
     */
    @Test
    void aThinlyPlayedStrategyDoesNotDisplaceAWinningIncumbent() {
        Map<String, Record> opponentRecords = new HashMap<>();
        Map<String, MapAwareRecord> mapRecords = new HashMap<>();
        List<Long> gameTimestamps = new ArrayList<>();
        opponentRecords.put("4Pool", thirtyPercentIncumbent(gameTimestamps));
        List<String> candidates = Arrays.asList("12Pool", "Overpool", "4Pool", "9PoolSpeed");
        long timestamp = gameTimestamps.size();
        for (String thin : Arrays.asList("12Pool", "Overpool", "9PoolSpeed")) {
            Record record = Record.builder().opener(thin).build();
            record.addLossTimestamp(++timestamp);
            record.setLosses(1);
            opponentRecords.put(thin, record);
            gameTimestamps.add(timestamp);
        }

        for (int lifetime : new int[] {33, 53, 339, 1324}) {
            assertEquals("4Pool", WeightedUCBCalculator.findBestStrategy(candidates, "MapA",
                    mapRecords, opponentRecords, lifetime, gameTimestamps),
                    "A thinly played arm displaced a 30% incumbent at a lifetime of " + lifetime);
        }
    }

    private static Record thirtyPercentIncumbent(List<Long> gameTimestamps) {
        Record incumbent = Record.builder().opener("4Pool").build();
        for (long timestamp = 1; timestamp <= 30; timestamp++) {
            gameTimestamps.add(timestamp);
            if (timestamp % 10 == 3 || timestamp % 10 == 6 || timestamp % 10 == 9) {
                incumbent.addWinTimestamp(timestamp);
                incumbent.setWins(incumbent.getWins() + 1);
            } else {
                incumbent.addLossTimestamp(timestamp);
                incumbent.setLosses(incumbent.getLosses() + 1);
            }
        }
        return incumbent;
    }

    @Test
    void theMapClockCountsOnlyGamesPlayedOnThatMap() {
        Map<String, MapAwareRecord> records = new HashMap<>();
        records.put(WeightedUCBCalculator.createMapKey("MapA", "4Pool"), mapRecord("MapA", "4Pool", 1L, 2L));
        records.put(WeightedUCBCalculator.createMapKey("MapA", "Overpool"), mapRecord("MapA", "Overpool", 3L));
        records.put(WeightedUCBCalculator.createMapKey("MapB", "4Pool"), mapRecord("MapB", "4Pool", 4L, 5L, 6L));

        assertEquals(3, WeightedUCBCalculator.mapClock(records, "MapA").size());
        assertEquals(3, WeightedUCBCalculator.mapClock(records, "MapB").size());
        assertEquals(0, WeightedUCBCalculator.mapClock(records, "MapC").size());
    }

    @Test
    void aMapRecordAgesOnItsOwnMapNotOnEveryOpponentGame() {
        MapAwareRecord record = mapRecord("MapA", "4Pool", 1L, 15L, 29L);
        Map<String, MapAwareRecord> records = new HashMap<>();
        records.put(WeightedUCBCalculator.createMapKey("MapA", "4Pool"), record);

        List<Long> opponentClock = new ArrayList<>();
        for (long timestamp = 1; timestamp <= 42; timestamp++) {
            opponentClock.add(timestamp);
        }

        double onMapClock = record.discountedGames(WeightedUCBCalculator.mapClock(records, "MapA"));
        double onOpponentClock = record.discountedGames(opponentClock);

        assertTrue(onMapClock > onOpponentClock * 3,
                "three visits spread over 42 opponent games must retain far more weight on the map's own "
                        + "clock, measured " + onMapClock + " against " + onOpponentClock);
    }

    @Test
    void theBlendWeighsEvidenceItActuallyScored() {
        MapAwareRecord stale = mapRecord("MapA", "4Pool", 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        Map<String, MapAwareRecord> records = new HashMap<>();
        records.put(WeightedUCBCalculator.createMapKey("MapA", "4Pool"), stale);

        double discounted = stale.discountedGames(WeightedUCBCalculator.mapClock(records, "MapA"));

        assertTrue(discounted < stale.games(),
                "discounted evidence must be no larger than the raw count it replaces in the sigmoid");
        assertTrue(discounted > 0.0, "a record with games must carry some discounted weight");
    }

    /** Wins, so the fixture decays at GAMMA_WIN and the clock difference is not muffled by the slower loss decay. */
    private MapAwareRecord mapRecord(String mapName, String strategy, long... winTimestamps) {
        MapAwareRecord record = MapAwareRecord.builder().strategy(strategy).mapName(mapName).build();
        for (long timestamp : winTimestamps) {
            record.addWinTimestamp(timestamp);
            record.setWins(record.getWins() + 1);
        }
        return record;
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
