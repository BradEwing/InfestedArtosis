package telemetry;

import bwapi.Game;
import bwapi.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes telemetry_lurker_burrow.csv: one row per burrow or unburrow command a Lurker issues.
 *
 * <p>command is BURROW or UNBURROW and reason is a {@link BurrowReason}. role is the Lurker's role when it issued the
 * command, x and y are where it stood, and contain_x and contain_y are its contain point, -1 when it has none.
 * withdraw_x and withdraw_y are where an UNDER_FIRE_WITHDRAW sends the Lurker and withdraw_zones is how many zones of
 * shooters that outrange it cover where it stood, 0 when no known shooter does; all three are -1 on any other row.
 * BURROW_REFUSED_UNDER_FIRE rows, command BURROW, are burrows the Lurker did not issue because it stood inside
 * enemy fire it cannot answer. BURROW_UNDER_FIRE_ALLOWED_ENEMY_IN_RANGE and BURROW_UNDER_FIRE_ALLOWED_LOSING_HP rows,
 * command BURROW, are burrows it issued inside that fire because a ground enemy was in its weapon range with only a
 * hit mark covering it, or because it was losing hit points with the safe point beyond 64 px. All three are written
 * at most once per 96 frames per Lurker, and none changes the burrow state last commanded. A Lurker that died with no
 * attack started can be classified by the last row of its unit id.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class BurrowLogger implements BurrowSink {

    static final String FILE = "telemetry_lurker_burrow.csv";

    static final String HEADER = "game_id,frame,unit_id,command,reason,role,x,y,hit_points,contain_x,contain_y,withdraw_x,withdraw_y,withdraw_zones";

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
    public void onBurrowCommand(BurrowCommand command) {
        if (disabled) {
            return;
        }

        try {
            writer.append(gameId + "," + row(command));
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
     * @param command the command
     * @return the row
     */
    static String row(BurrowCommand command) {
        Position position = command.getPosition();
        Position containPoint = command.getContainPoint();
        Position withdrawPoint = command.getWithdrawPoint();
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(command.getFrame()));
        fields.add(String.valueOf(command.getUnitId()));
        fields.add(command.isBurrow() ? "BURROW" : "UNBURROW");
        fields.add(Csv.name(command.getReason()));
        fields.add(Csv.sanitize(command.getRole()));
        fields.add(String.valueOf(position == null ? NONE : position.getX()));
        fields.add(String.valueOf(position == null ? NONE : position.getY()));
        fields.add(String.valueOf(command.getHitPoints()));
        fields.add(String.valueOf(containPoint == null ? NONE : containPoint.getX()));
        fields.add(String.valueOf(containPoint == null ? NONE : containPoint.getY()));
        fields.add(String.valueOf(withdrawPoint == null ? NONE : withdrawPoint.getX()));
        fields.add(String.valueOf(withdrawPoint == null ? NONE : withdrawPoint.getY()));
        fields.add(String.valueOf(command.getWithdrawZones()));
        return String.join(",", fields);
    }
}
