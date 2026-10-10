package learning;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Writes the learning row for a game that never reaches the bot's end-of-game callback.
 *
 * <p>A game that runs to the frame cap is ended from outside and the bot gets no end callback, so no row would
 * be written and the opener and build that stalled would look untouched to the bandit. Once the game clock
 * reaches {@link #FRAME} the recorder writes one loss row marked as a stalemate. A real result that still
 * arrives replaces that row, so a game is never counted twice.
 */
final class StalemateRecorder {
    /**
     * Game frame at which the stalemate row is written: 60 game minutes at 24 frames per second. Every observed
     * frame-cap end came at or after frame 86568, and a game that is still being played at this frame can finish
     * later with a real result, which replaces the row.
     */
    static final int FRAME = 86400;

    private final LearningHistoryRepository repository;
    private GameRecord written;

    StalemateRecorder(LearningHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Writes the stalemate row once, on the first frame at or past {@link #FRAME}.
     */
    void onFrame(int frame, Supplier<GameRecord> stalemateRow) {
        if (written != null || frame < FRAME) {
            return;
        }
        GameRecord row = stalemateRow.get();
        try {
            repository.append(row);
        } catch (IOException e) {
            return;
        }
        written = row;
    }

    /**
     * Writes the game's real result, replacing the stalemate row when one was written.
     */
    void onEnd(GameRecord result) {
        try {
            if (written == null) {
                repository.append(result);
            } else {
                repository.replaceLast(written, result);
            }
        } catch (IOException e) {
            return;
        }
        written = null;
    }

    boolean hasWritten() {
        return written != null;
    }
}
