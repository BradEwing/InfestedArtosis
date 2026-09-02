package learning;

import java.util.Collections;
import java.util.List;

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
}
