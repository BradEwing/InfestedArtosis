package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the beta3 ZurZurZur learning record through OpenerSelectionPolicy.select. The recorded
 * games up to the point every opener had failed its cold-start trial seed the history. Every later
 * game keeps its recorded map and result while the policy picks the opener, so the policy reads a
 * history built from its own choices.
 */
public class OpenerSelectionReplayTest {

    private static final String RECORD = "/learning/ZurZurZur_Zerg_beta3.csv";
    private static final String OPPONENT = "ZurZurZur";
    private static final int SEEDED_GAMES = 16;

    @Test
    void beta3ZurZurZurReplaySelectsTwelveHatchAfterEveryTrialFails() throws IOException {
        List<GameRecord> recorded = loadRecord();
        LearningRecordAccumulator accumulator = new LearningRecordAccumulator(OPPONENT, Race.Zerg);
        OpponentRecord opponentRecord = accumulator.reconstruct(
                new LearningHistory(new ArrayList<>(recorded.subList(0, SEEDED_GAMES))));
        GameRecord lastGame = recorded.get(SEEDED_GAMES - 1);
        String lastGameOpener = lastGame.getOpener();
        String lastGameDetectedStrategies = lastGame.getDetectedStrategies();

        List<String> selected = new ArrayList<>();
        for (GameRecord game : recorded.subList(SEEDED_GAMES, recorded.size())) {
            BuildOrderFactory factory = new BuildOrderFactory(game.getNumStartingLocations(), Race.Zerg);
            for (String opener : factory.getOpenerNames()) {
                opponentRecord.getOpenerRecord().putIfAbsent(opener,
                        Record.builder().opener(opener).wins(0).losses(0).build());
            }
            String opener = OpenerSelectionPolicy.select(null, factory, opponentRecord,
                    lastGameDetectedStrategies, lastGameOpener, game.getMapName());
            accumulator.apply(opponentRecord, GameRecord.builder()
                    .timestamp(game.getTimestamp())
                    .numStartingLocations(game.getNumStartingLocations())
                    .mapName(game.getMapName())
                    .opponentName(game.getOpponentName())
                    .opponentRace(game.getOpponentRace())
                    .opener(opener)
                    .buildOrder(game.getBuildOrder())
                    .detectedStrategies(game.getDetectedStrategies())
                    .isWinner(game.isWinner())
                    .frameCount(game.getFrameCount())
                    .build());
            selected.add(opener);
            lastGameOpener = opener;
            lastGameDetectedStrategies = game.getDetectedStrategies();
        }

        assertTrue(selected.contains("12Hatch"),
                "replay never selected 12Hatch after every opener failed its trial: " + selected);
    }

    private static List<GameRecord> loadRecord() throws IOException {
        List<GameRecord> games = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                OpenerSelectionReplayTest.class.getResourceAsStream(RECORD), StandardCharsets.UTF_8))) {
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
