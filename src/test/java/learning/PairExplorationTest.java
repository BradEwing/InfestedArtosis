package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With every build order at zero wins the openers and builds rotate in lockstep, so each opener
 * keeps drawing the same build. Selection must pair each opener with the builds it has met least
 * and must not change any choice once a build has won.
 */
public class PairExplorationTest {

    private static final String RECORD = "/learning/PurpleWave_Protoss_control3.csv";
    private static final String OPPONENT = "PurpleWave";
    private static final String MAP_NAME = "(4)Polypoid_1.65.scx";
    private static final String HYDRA = "3HatchHydra";
    private static final String MUTA = "3HatchMuta";
    private static final String SPEEDLING = "SpeedlingAllIn";
    private static final List<String> CANDIDATES = Arrays.asList(HYDRA, MUTA, SPEEDLING);
    private static final int REPEAT_OPENER_DEADLINE = 8;
    private static final int SPEEDLING_PAIR_DEADLINE = 21;
    private static final int SPEEDLING_PAIR_GAME = 11;
    private static final int PAIRS_BY_DEADLINE = 18;

    @Test
    void anAllLossHistoryOffersTheOpenerABuildItHasNotMet() {
        OpponentRecord record = newRecord();
        LearningRecordAccumulator accumulator = accumulator();
        play(accumulator, record, "12Hatch", HYDRA, false);
        play(accumulator, record, "9Hatch", MUTA, false);
        play(accumulator, record, "12Pool", SPEEDLING, false);

        String selected = LearningManager.selectBuildOrderName(CANDIDATES, record, MAP_NAME, "12Hatch");

        assertNotEquals(HYDRA, selected);
    }

    @Test
    void theFirstCreditedBuildOfAGameIsTheOnlyOneCountedAgainstTheOpener() {
        OpponentRecord record = newRecord();
        LearningRecordAccumulator accumulator = accumulator();
        play(accumulator, record, "12Hatch", HYDRA + ";" + MUTA, false);

        assertEquals(Integer.valueOf(1), record.getOpenerBuildPairs().get("12Hatch|" + HYDRA));
        assertEquals(1, record.getOpenerBuildPairs().size());
    }

    @Test
    void aGameWithoutABuildCountsNoPair() {
        OpponentRecord record = newRecord();
        LearningRecordAccumulator accumulator = accumulator();
        play(accumulator, record, "4Pool", "", false);

        assertTrue(record.getOpenerBuildPairs().isEmpty());
    }

    @Test
    void onceAnyBuildHasAWinTheSelectionEqualsPlainUcb() {
        OpponentRecord record = newRecord();
        LearningRecordAccumulator accumulator = accumulator();
        play(accumulator, record, "12Hatch", HYDRA, false);
        play(accumulator, record, "9Hatch", MUTA, false);
        play(accumulator, record, "12Pool", SPEEDLING, true);
        for (String opener : Arrays.asList("12Hatch", "9Hatch", "12Pool", "Overpool")) {
            assertEquals(plainUcb(record), LearningManager.selectBuildOrderName(CANDIDATES, record, MAP_NAME, opener));
        }
    }

    @Test
    void anUntriedCandidateKeepsItsPriorityOverThePairRule() {
        OpponentRecord record = newRecord();
        LearningRecordAccumulator accumulator = accumulator();
        play(accumulator, record, "12Hatch", HYDRA, false);
        play(accumulator, record, "9Hatch", MUTA, false);
        record.getBuildOrderRecord().put(SPEEDLING, Record.builder().opener(SPEEDLING).wins(0).losses(0).build());

        assertEquals(plainUcb(record), LearningManager.selectBuildOrderName(CANDIDATES, record, MAP_NAME, "12Hatch"));
    }

    @Test
    void anEarlySpeedlingWinLeavesEveryChoiceUnchanged() throws IOException {
        List<GameRecord> maps = loadRecord();
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord record = newRecord(factory);
        LearningRecordAccumulator accumulator = accumulator();
        play(accumulator, record, "12Pool", HYDRA, false);
        play(accumulator, record, "9Hatch", SPEEDLING, true);
        String previous = "9Hatch";

        for (int game = 2; game < 40; game++) {
            String map = maps.get(game % maps.size()).getMapName();
            String opener = LearningManager.selectOpenerName(null, factory, record, "", previous, map);
            if (!"4Pool".equals(opener)) {
                String selected = LearningManager.selectBuildOrderName(CANDIDATES, record, map, opener);
                assertEquals(plainUcb(record, map), selected, "game " + (game + 1));
                play(accumulator, record, opener, selected, game % 4 == 0, map);
            } else {
                play(accumulator, record, opener, "", false, map);
            }
            previous = opener;
        }
    }

    @Test
    void aClosedLoopOfLossesRepeatsAnOpenerWithANewBuildAndReachesTwelveHatchSpeedling() throws IOException {
        List<GameRecord> maps = loadRecord();
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord record = newRecord(factory);
        LearningRecordAccumulator accumulator = accumulator();
        Set<String> seenOpeners = new HashSet<>();
        Set<String> seenPairs = new HashSet<>();
        int repeatOpenerNewPairGame = -1;
        int speedlingPairGame = -1;
        String previous = "";

        for (int game = 1; game <= SPEEDLING_PAIR_DEADLINE; game++) {
            String map = maps.get((game - 1) % maps.size()).getMapName();
            String opener = LearningManager.selectOpenerName(null, factory, record, "", previous, map);
            String build = "";
            if (!"4Pool".equals(opener)) {
                build = LearningManager.selectBuildOrderName(CANDIDATES, record, map, opener);
                String pair = opener + "|" + build;
                if (repeatOpenerNewPairGame < 0 && seenOpeners.contains(opener) && !seenPairs.contains(pair)) {
                    repeatOpenerNewPairGame = game;
                }
                if (speedlingPairGame < 0 && ("12Hatch|" + SPEEDLING).equals(pair)) {
                    speedlingPairGame = game;
                }
                seenPairs.add(pair);
            }
            seenOpeners.add(opener);
            play(accumulator, record, opener, build, false, map);
            previous = opener;
        }

        assertTrue(repeatOpenerNewPairGame > 0 && repeatOpenerNewPairGame <= REPEAT_OPENER_DEADLINE,
                "first repeat-opener game with a new pair: " + repeatOpenerNewPairGame);
        assertTrue(speedlingPairGame > 0 && speedlingPairGame <= SPEEDLING_PAIR_GAME, "12Hatch/" + SPEEDLING
                + " first played at game " + speedlingPairGame + ", pairs: " + seenPairs);
        assertEquals(PAIRS_BY_DEADLINE, seenPairs.size());
    }

    private static String plainUcb(OpponentRecord record) {
        return plainUcb(record, MAP_NAME);
    }

    private static String plainUcb(OpponentRecord record, String map) {
        return WeightedUCBCalculator.findBestStrategy(CANDIDATES, map, record.getMapSpecificBuildOrderRecord(),
                record.getBuildOrderRecord(), record.totalGames(), record.getGameTimestamps());
    }

    private static LearningRecordAccumulator accumulator() {
        return new LearningRecordAccumulator(OPPONENT, Race.Protoss);
    }

    private static OpponentRecord newRecord() {
        return accumulator().reconstruct(new LearningHistory(new ArrayList<>()));
    }

    private static OpponentRecord newRecord(BuildOrderFactory factory) {
        OpponentRecord record = newRecord();
        for (String name : factory.getOpenerNames()) {
            if (factory.isPlayableOpener(factory.getByName(name))) {
                record.getOpenerRecord().put(name, Record.builder().opener(name).wins(0).losses(0).build());
            }
        }
        return record;
    }

    private static void play(LearningRecordAccumulator accumulator, OpponentRecord record, String opener,
                             String build, boolean won) {
        play(accumulator, record, opener, build, won, MAP_NAME);
    }

    private static void play(LearningRecordAccumulator accumulator, OpponentRecord record, String opener,
                             String build, boolean won, String map) {
        long timestamp = record.getGameTimestamps().size() + 1L;
        accumulator.apply(record, GameRecord.builder()
                .timestamp(timestamp)
                .numStartingLocations(4)
                .mapName(map)
                .opponentName(OPPONENT)
                .opponentRace("Protoss")
                .opener(opener)
                .buildOrder(build)
                .detectedStrategies("")
                .isWinner(won)
                .frameCount(10000)
                .build());
        for (String name : CANDIDATES) {
            record.getBuildOrderRecord().putIfAbsent(name, Record.builder().opener(name).wins(0).losses(0).build());
        }
    }

    private static List<GameRecord> loadRecord() throws IOException {
        List<GameRecord> games = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                PairExplorationTest.class.getResourceAsStream(RECORD), StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    games.add(GameRecord.fromCsvRow(line));
                }
            }
        }
        return games;
    }
}
