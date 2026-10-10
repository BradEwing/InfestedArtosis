package telemetry;

import bwapi.Game;
import info.GameState;

import java.util.Arrays;

/**
 * Writes telemetry_bunker.csv: the rows about enemy Bunkers, told apart by row_type.
 *
 * <p>BUNKER_ECON: one row per change of a Bunker stance, see the Drone round it opens. event is STANCE_START,
 * ROUND_OPEN, ROUND_CLOSE or STANCE_END. stance_id numbers the game's Bunker holds from 1, and a stance that re-forms
 * inside one hold keeps the id. drones counts Drones hatched or in an egg, workers the workers gathering,
 * extra_planned the Drones the hold's rounds were set to add so far and extra_made the Drones queued at
 * {@link macro.plan.UnitPlan#DRONE_ROUND_PRIORITY} that were made so far. A round or stance still open when the
 * game ends is closed with the reason GAME_END.
 *
 * <p>The header carries columns for other Bunker row types; their cells stay empty in a BUNKER_ECON row.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class BunkerLogger implements BunkerSink {

    static final String FILE = "telemetry_bunker.csv";

    static final String[] COLUMNS = {
        "game_id", "frame", "row_type", "event", "squad_id", "reason", "own_strength", "bunker_price", "ratio",
        "release_ratio", "ling_count", "bunker_id", "bunker_x", "bunker_y", "bunker_hp", "engagement_id",
        "start_frame", "end_frame", "our_lost", "lings_lost", "enemy_lost", "hp_start", "hp_end", "broken",
        "stance_id", "drones", "workers", "extra_planned", "extra_made", "squad_size", "squad_x", "squad_y", "entry",
        "units_lost"
    };

    static final String HEADER = String.join(",", COLUMNS);

    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private final Game game;
    private final GameState gameState;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public BunkerLogger(Game game, GameState gameState, String gameId) {
        this(game, gameState, gameId, new TelemetryWriter(FILE, HEADER));
    }

    BunkerLogger(Game game, GameState gameState, String gameId, TelemetryWriter writer) {
        this.game = game;
        this.gameState = gameState;
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
            gameState.getBunkerStance().flush(game.getFrameCount());
            writer.flush();
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onStance(BunkerStanceEvent event) {
        if (disabled) {
            return;
        }

        try {
            writer.append(econRow(gameId, event));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        BunkerTelemetry.clear();
    }

    private static String[] blankRow(String gameId, int frame, String rowType, String event) {
        String[] cells = new String[COLUMNS.length];
        Arrays.fill(cells, "");
        cells[0] = gameId;
        cells[1] = String.valueOf(frame);
        cells[2] = rowType;
        cells[3] = Csv.sanitize(event);
        return cells;
    }

    private static void set(String[] cells, String column, String value) {
        for (int i = 0; i < COLUMNS.length; i++) {
            if (COLUMNS[i].equals(column)) {
                cells[i] = value;
                return;
            }
        }
        throw new IllegalArgumentException(column);
    }

    /**
     * Builds a BUNKER_ECON row in {@link #HEADER} order.
     *
     * @return the row
     */
    static String econRow(String gameId, BunkerStanceEvent event) {
        String[] cells = blankRow(gameId, event.getFrame(), "BUNKER_ECON", event.getEvent());
        set(cells, "reason", Csv.sanitize(event.getReason()));
        set(cells, "stance_id", String.valueOf(event.getStanceId()));
        set(cells, "drones", String.valueOf(event.getDrones()));
        set(cells, "workers", String.valueOf(event.getWorkers()));
        set(cells, "extra_planned", String.valueOf(event.getExtraPlanned()));
        set(cells, "extra_made", String.valueOf(event.getExtraMade()));
        return String.join(",", cells);
    }
}
