package learning;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class LearningHistory {
    private final List<GameRecord> games;

    LearningHistory(List<GameRecord> games) {
        this.games = games;
    }

    List<GameRecord> games() {
        return Collections.unmodifiableList(games);
    }

    GameRecord lastGame() {
        return games.isEmpty() ? null : games.get(games.size() - 1);
    }

    /**
     * The detected strategies of the last count games, oldest first, fewer when fewer games were played.
     */
    List<String> lastGamesDetectedStrategies(int count) {
        return games.subList(Math.max(0, games.size() - count), games.size())
                .stream()
                .map(GameRecord::getDetectedStrategies)
                .collect(Collectors.toList());
    }
}
