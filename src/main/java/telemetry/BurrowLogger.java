package telemetry;

import bwapi.Game;
import bwapi.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes telemetry_lurker_burrow.csv: one row per burrow or unburrow command a Lurker issues.
 *
 * <p>command is BURROW or UNBURROW and reason is a {@link BurrowReason}. role is the Lurker's role when it issued the
 * command, x and y are where it stood, and contain_x and contain_y are its contain point, -1 when it has none. A
 * Lurker that died with no attack started can be classified by the last row of its unit id.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class BurrowLogger implements BurrowSink {

    static final String FILE = "telemetry_lurker_burrow.csv";

    static final String HEADER = "game_id,frame,unit_id,command,reason,role,x,y,hit_points,contain_x,contain_y";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NONE = -1;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public BurrowLogger(Game game, String gameId) {
        this(game, gameId, new TelemetryWriter(FILE, HEADER));
    }

    BurrowLogger(Game game, String gameId, TelemetryWriter writer) {
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
    public void onBurrowCommand(int frame, int unitId, boolean burrow, BurrowReason reason, String role,
                                Position position, int hitPoints, Position containPoint) {
        if (disabled) {
            return;
        }

        try {
            writer.append(gameId + "," + row(frame, unitId, burrow, reason, role, position, hitPoints, containPoint));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        BurrowTelemetry.clear();
    }

    /**
     * Builds one CSV row in {@link #HEADER} order, without the leading game_id.
     *
     * @return the row
     */
    static String row(int frame, int unitId, boolean burrow, BurrowReason reason, String role,
                      Position position, int hitPoints, Position containPoint) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(frame));
        fields.add(String.valueOf(unitId));
        fields.add(burrow ? "BURROW" : "UNBURROW");
        fields.add(Csv.name(reason));
        fields.add(Csv.sanitize(role));
        fields.add(String.valueOf(position == null ? NONE : position.getX()));
        fields.add(String.valueOf(position == null ? NONE : position.getY()));
        fields.add(String.valueOf(hitPoints));
        fields.add(String.valueOf(containPoint == null ? NONE : containPoint.getX()));
        fields.add(String.valueOf(containPoint == null ? NONE : containPoint.getY()));
        return String.join(",", fields);
    }
}
