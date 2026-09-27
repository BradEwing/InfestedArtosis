package telemetry;

import bwapi.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes telemetry_flock.csv.
 *
 * <p>SAMPLE rows are written every {@link #SAMPLE_INTERVAL_FRAMES} frames for each air squad with at least two
 * Mutalisks in FIGHT, HARASS or RETREAT: its Mutalisk count, their centroid, the median and largest Mutalisk
 * distance to that centroid, and how many Mutalisks are regrouping on the flock. MUTA_LOST rows are written for every
 * Mutalisk of ours that dies: its squad, if any, the squad's status and Mutalisk count including the dead one, the
 * death position in centroid_x and centroid_y, and nearest_mate_distance, the pixels to the nearest other member of
 * its squad, or -1 with none.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class FlockLogger implements FlockSink {

    static final String FILE = "telemetry_flock.csv";

    static final String HEADER = "game_id,frame,squad_id,event,status,mutas,centroid_x,centroid_y,median_distance,"
            + "max_distance,regrouping,unit_id,nearest_mate_distance";

    /** Frames between two SAMPLE rows of one squad. */
    public static final int SAMPLE_INTERVAL_FRAMES = 24;

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public FlockLogger(Game game, String gameId) {
        this(game, gameId, new TelemetryWriter(FILE, HEADER));
    }

    FlockLogger(Game game, String gameId, TelemetryWriter writer) {
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
    public void onRow(FlockRow row) {
        if (disabled) {
            return;
        }

        try {
            writer.append(row(gameId, row));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        FlockTelemetry.clear();
    }

    /**
     * Builds one CSV row in {@link #HEADER} order.
     *
     * @param gameId game id
     * @param row the row to record
     * @return the row
     */
    static String row(String gameId, FlockRow row) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(row.getFrame()));
        fields.add(Csv.name(row.getSquadId()));
        fields.add(Csv.name(row.getEvent()));
        fields.add(Csv.name(row.getStatus()));
        fields.add(String.valueOf(row.getMutas()));
        fields.add(String.valueOf(row.getCentroid() == null ? NOT_EVALUATED : row.getCentroid().getX()));
        fields.add(String.valueOf(row.getCentroid() == null ? NOT_EVALUATED : row.getCentroid().getY()));
        fields.add(Csv.format(row.getMedianDistance()));
        fields.add(Csv.format(row.getMaxDistance()));
        fields.add(String.valueOf(row.getRegrouping()));
        fields.add(String.valueOf(row.getUnitId()));
        fields.add(Csv.format(row.getNearestMateDistance()));
        return String.join(",", fields);
    }
}
