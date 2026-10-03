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
 * 3HatchLurker lost its first games there, and the arms that followed won now and then. Every later
 * game is a loss, so the incumbent decays and 3HatchLurker has to be offered again.
 */
public class BuildOrderCuriosityReplayTest {

    private static final String RECORD = "/learning/d_beta_void_first20_narrow.csv";
    private static final String OPPONENT = "VOID";
    private static final String RETRIED = "3HatchLurker";
    private static final String OPENER = "9PoolSpeed";
    private static final int RETRY_WINDOW = 5;
    private static final List<String> CANDIDATES = Arrays.asList(
            "2HatchMuta", "3HatchHydraZvT", RETRIED, "CrazyZerg", "SpeedlingAllIn");

    @Test
    void threeHatchLurkerIsOfferedAgainWithinFiveGamesOfLaterLosses() throws IOException {
        List<GameRecord> recorded = loadRecord();
        LearningRecordAccumulator accumulator = new LearningRecordAccumulator(OPPONENT, Race.Terran);
        OpponentRecord opponentRecord = accumulator.reconstruct(new LearningHistory(new ArrayList<>(recorded)));
        for (String candidate : CANDIDATES) {
            opponentRecord.getBuildOrderRecord().putIfAbsent(candidate,
                    Record.builder().opener(candidate).wins(0).losses(0).build());
        }

        List<String> selected = new ArrayList<>();
        long timestamp = recorded.get(recorded.size() - 1).getTimestamp();
        for (int game = 0; game < RETRY_WINDOW; game++) {
            GameRecord template = recorded.get(game % recorded.size());
            String buildOrder = LearningManager.selectBuildOrderName(
                    CANDIDATES, opponentRecord, template.getMapName());
            selected.add(buildOrder);
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
                    .isWinner(false)
                    .frameCount(template.getFrameCount())
                    .build());
        }

        assertTrue(selected.contains(RETRIED),
                RETRIED + " was not offered within " + RETRY_WINDOW + " losing games: " + selected);
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
