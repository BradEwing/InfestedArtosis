package telemetry;

import bwapi.Game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Writes telemetry_flock.csv.
 *
 * <p>SAMPLE rows are written every {@link #SAMPLE_INTERVAL_FRAMES} frames for each air squad with at least two
 * Mutalisks in FIGHT, HARASS or RETREAT: its Mutalisk count, their centroid, the median and largest Mutalisk
 * distance to that centroid, how many Mutalisks are regrouping on the flock, their unit ids in regrouping_ids, and in
 * regrouping_armed how many of those still attack a target within their weapon range; the regrouping columns are -1
 * in RETREAT, where a flock flees together and nothing regroups.
 *
 * <p>MUTA_LOST rows are written for every Mutalisk of ours that dies: its squad, if any, the squad's status and
 * Mutalisk count including the dead one, the death position in centroid_x and centroid_y, nearest_mate_distance, the
 * pixels to the nearest other Mutalisk of its squad, or -1 with none, the squad's regrouping_ids, and last_muta, 1
 * when no other Mutalisk is in its squad and 0 otherwise. A Mutalisk with no Mutalisk squad-mate within 256 pixels
 * died alone.
 *
 * <p>REGROUP rows are written for each Mutalisk the frame it starts regrouping on its flock: its squad and status,
 * its position in centroid_x and centroid_y, nearest_mate_distance to the nearest other Mutalisk of its squad, and
 * regrouping_armed, 1 when it kept attacking a target within its weapon range and 0 when it flew back unarmed.
 *
 * <p>regrouping_ids is a semicolon separated list, ascending, empty with none regrouping and -1 when not evaluated.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class FlockLogger implements FlockSink {

    static final String FILE = "telemetry_flock.csv";

    static final String HEADER = "game_id,frame,squad_id,event,status,mutas,centroid_x,centroid_y,median_distance,"
            + "max_distance,regrouping,unit_id,nearest_mate_distance,regrouping_ids,regrouping_armed,last_muta";

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
        fields.add(ids(row.getRegroupingIds()));
        fields.add(String.valueOf(row.getRegroupingArmed()));
        fields.add(String.valueOf(row.getLastMuta()));
        return String.join(",", fields);
    }

    private static String ids(Collection<Integer> ids) {
        if (ids == null) {
            return String.valueOf(NOT_EVALUATED);
        }
        List<Integer> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        List<String> parts = new ArrayList<>();
        for (Integer id : sorted) {
            parts.add(String.valueOf(id));
        }
        return String.join(";", parts);
    }
}
