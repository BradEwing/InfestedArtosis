package telemetry;

import bwapi.Game;
import bwapi.Position;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes telemetry_reach.csv: one row each time the learned ground reach of an enemy type rises, and one row per
 * new hurt mark.
 *
 * <p>source names what raised the reach: API when the owner's weapon reports a longer range than was known,
 * VISIBLE for a hit by a visible enemy, BULLET for a Marine shot out of a Bunker, and HURTMARK for a hit nothing
 * known accounts for. A HURTMARK row carries NONE as the type, -1 as the old reach and the mark's radius as the new
 * one. victim_x and victim_y are where the unit that was hit stood, -1 on an API row.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class ReachLogger implements ReachSink {

    static final String FILE = "telemetry_reach.csv";

    static final String HEADER = "game_id,frame,unit_type,old_reach,new_reach,source,victim_x,victim_y";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public ReachLogger(Game game, String gameId) {
        this(game, gameId, new TelemetryWriter(FILE, HEADER));
    }

    ReachLogger(Game game, String gameId, TelemetryWriter writer) {
        this.game = game;
        this.gameId = gameId;
        this.writer = writer;
    }

    public void onFrame() {
        if (disabled) {
            return;
        }

        try {
            if (game.getFrameCount() % FLUSH_INTERVAL_FRAMES == 0) {
                writer.flush();
            }
        } catch (RuntimeException e) {
            disable();
        }
    }

    public void onEnd() {
        if (disabled) {
            return;
        }

        try {
            writer.flush();
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onReachRaised(int frame, UnitType type, int oldReach, int newReach, EnemyReachMemory.Source source,
                              Position victim) {
        if (disabled) {
            return;
        }

        try {
            writer.append(row(gameId, frame, type, oldReach, newReach, source, victim));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        ReachTelemetry.clear();
    }

    /**
     * Builds one CSV row in {@link #HEADER} order.
     *
     * @return the row
     */
    static String row(String gameId, int frame, UnitType type, int oldReach, int newReach,
                      EnemyReachMemory.Source source, Position victim) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(frame));
        fields.add(Csv.name(type));
        fields.add(String.valueOf(oldReach));
        fields.add(String.valueOf(newReach));
        fields.add(Csv.name(source));
        fields.add(String.valueOf(victim == null ? NOT_EVALUATED : victim.getX()));
        fields.add(String.valueOf(victim == null ? NOT_EVALUATED : victim.getY()));
        return String.join(",", fields);
    }
}
