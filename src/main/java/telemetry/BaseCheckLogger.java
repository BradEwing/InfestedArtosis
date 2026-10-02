package telemetry;

import bwapi.Game;
import bwapi.TilePosition;
import bwapi.UnitType;
import unit.scout.BaseCheckScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * Records one row per base check: the base, the frame the scout was sent, the frame the check ended, why it
 * ended, and whether an enemy stood within sight of the base.
 *
 * <p>end_frame is the arrival frame when outcome is SEEN; for every other outcome the base was not reached
 * and end_frame is when the scout was recalled, lost or timed out. age_at_dispatch is -1 for a base that had
 * never been seen. occupied is 1 only when an enemy was sighted near the base at end_frame.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class BaseCheckLogger implements BaseCheckSink {

    static final String FILE = "telemetry_base_checks.csv";

    static final String HEADER = "game_id,unit_id,unit_type,base_x,base_y,age_at_dispatch,dispatch_frame,"
            + "end_frame,outcome,occupied";

    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public BaseCheckLogger(Game game, String gameId) {
        this.game = game;
        this.gameId = gameId;
        this.writer = new TelemetryWriter(FILE, HEADER);
    }

    public void onFrame() {
        if (disabled) {
            return;
        }

        try {
            if (game.getFrameCount() % FLUSH_INTERVAL_FRAMES == 0) {
                writer.flush();
            }
        } catch (Exception e) {
            disabled = true;
        }
    }

    public void onEnd() {
        if (disabled) {
            return;
        }

        try {
            writer.flush();
        } catch (Exception e) {
            disabled = true;
        }
    }

    @Override
    public void onBaseChecked(int unitId, UnitType unitType, TilePosition base, int ageAtDispatch,
                              int dispatchFrame, int endFrame, BaseCheckScheduler.Release outcome,
                              boolean occupied) {
        if (disabled) {
            return;
        }

        try {
            writer.append(gameId + "," + row(unitId, unitType, base, ageAtDispatch, dispatchFrame, endFrame, outcome,
                    occupied));
        } catch (Exception e) {
            disabled = true;
        }
    }

    static String row(int unitId, UnitType unitType, TilePosition base, int ageAtDispatch,
                      int dispatchFrame, int endFrame, BaseCheckScheduler.Release outcome, boolean occupied) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(unitId));
        fields.add(Csv.name(unitType));
        fields.add(String.valueOf(base.getX()));
        fields.add(String.valueOf(base.getY()));
        fields.add(String.valueOf(ageAtDispatch < 0 || ageAtDispatch == Integer.MAX_VALUE ? -1 : ageAtDispatch));
        fields.add(String.valueOf(dispatchFrame));
        fields.add(String.valueOf(endFrame));
        fields.add(Csv.name(outcome));
        fields.add(occupied ? "1" : "0");
        return String.join(",", fields);
    }
}
