package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the first 20 games of the beta VOID cold start through LearningManager.selectBuildOrderName.
 * 3HatchLurker lost its first games there while SpeedlingAllIn won now and then. In every later game
 * SpeedlingAllIn wins one play in three and every other arm loses, and 3HatchLurker has to be offered again.
 */
public class BuildOrderCuriosityReplayTest {

    private static final String RECORD = "/learning/d_beta_void_first20_narrow.csv";
    private static final String OPPONENT = "VOID";
    private static final String RETRIED = "3HatchLurker";
    private static final String OPENER = "9PoolSpeed";
    private static final int RETRY_WINDOW = 10;
    private static final String INCUMBENT = "SpeedlingAllIn";
    private static final int INCUMBENT_WIN_EVERY = 3;
    private static final List<String> CANDIDATES = Arrays.asList(
            "2HatchMuta", RETRIED, "CrazyZerg", INCUMBENT);

    @Test
    void threeHatchLurkerIsOfferedAgainWithinTenGamesWhileTheIncumbentKeepsWinningOccasionally() throws IOException {
        List<GameRecord> recorded = loadRecord();
        LearningRecordAccumulator accumulator = new LearningRecordAccumulator(OPPONENT, Race.Terran);
        OpponentRecord opponentRecord = accumulator.reconstruct(new LearningHistory(new ArrayList<>(recorded)));
        for (String candidate : CANDIDATES) {
            opponentRecord.getBuildOrderRecord().putIfAbsent(candidate,
                    Record.builder().opener(candidate).wins(0).losses(0).build());
        }

        List<String> selected = new ArrayList<>();
        int incumbentPlays = 0;
        long timestamp = recorded.get(recorded.size() - 1).getTimestamp();
        for (int game = 0; game < RETRY_WINDOW; game++) {
            GameRecord template = recorded.get(game % recorded.size());
            String buildOrder = LearningManager.selectBuildOrderName(
                    CANDIDATES, opponentRecord, template.getMapName(), OPENER);
            selected.add(buildOrder);
            boolean won = false;
            if (INCUMBENT.equals(buildOrder)) {
                incumbentPlays++;
                won = incumbentPlays % INCUMBENT_WIN_EVERY == 0;
            }
            timestamp += 1000;
            accumulator.apply(opponentRecord, GameRecord.builder()
                    .timestamp(timestamp)
                    .numStartingLocations(template.getNumStartingLocations())
                    .mapName(template.getMapName())
                    .opponentName(OPPONENT)
                    .opponentRace("Terran")
                    .opener(OPENER)
                    .buildOrder(buildOrder)
                    .detectedStrategies(template.getDetectedStrategies())
                    .isWinner(won)
                    .frameCount(template.getFrameCount())
                    .build());
        }

        assertTrue(selected.contains(RETRIED),
                RETRIED + " was not offered within " + RETRY_WINDOW + " games: " + selected);
    }

    private static List<GameRecord> loadRecord() throws IOException {
        List<GameRecord> games = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                BuildOrderCuriosityReplayTest.class.getResourceAsStream(RECORD), StandardCharsets.UTF_8))) {
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
