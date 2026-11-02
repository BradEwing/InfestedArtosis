package telemetry;

import bwapi.Game;
import bwapi.Position;
import unit.managed.UnitRole;
import unit.squad.OverlordParking;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes telemetry_overlord_park.csv.
 *
 * <p>ANCHOR rows are written when a parked Overlord's anchor changes: x and y are where the Overlord is, from_x and
 * from_y the previous anchor, -1 for none, to_x and to_y the new anchor, and reason one of ASSIGNED,
 * SPORE_COMPLETED, IN_REACH, NEARER_SPORE, SPORE_LOST or OUT_OF_REACH. role is the Overlord's role, and spore_distance and
 * parked are -1 and -1 on these rows.
 *
 * <p>DIED rows are written for every Overlord of ours that dies: x and y are the death position, role its role,
 * spore_distance the pixels to the nearest completed own Spore Colony, -1 with none, and parked 1 when it was in the
 * Overlord squad holding the IDLE or RALLY role and 0 otherwise. from_x, from_y, to_x, to_y and reason are -1, -1, -1, -1 and NONE on these rows.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class OverlordParkLogger implements OverlordParkSink {

    static final String FILE = "telemetry_overlord_park.csv";

    static final String HEADER = "game_id,frame,event,unit_id,x,y,from_x,from_y,to_x,to_y,reason,role,"
            + "spore_distance,parked";

    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public OverlordParkLogger(Game game, String gameId) {
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
    public void onAnchorChanged(int frame, int unitId, Position position, Position from, Position to,
                                OverlordParking.Reason reason, UnitRole role) {
        if (disabled) {
            return;
        }

        try {
            writer.append(gameId + "," + anchorRow(frame, unitId, position, from, to, reason, role));
        } catch (Exception e) {
            disabled = true;
        }
    }

    @Override
    public void onOverlordDied(int frame, int unitId, Position position, UnitRole role, double sporeDistance,
                               boolean parked) {
        if (disabled) {
            return;
        }

        try {
            writer.append(gameId + "," + diedRow(frame, unitId, position, role, sporeDistance, parked));
        } catch (Exception e) {
            disabled = true;
        }
    }

    static String anchorRow(int frame, int unitId, Position position, Position from, Position to,
                            OverlordParking.Reason reason, UnitRole role) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(frame));
        fields.add("ANCHOR");
        fields.add(String.valueOf(unitId));
        fields.add(String.valueOf(position.getX()));
        fields.add(String.valueOf(position.getY()));
        fields.add(String.valueOf(from == null ? -1 : from.getX()));
        fields.add(String.valueOf(from == null ? -1 : from.getY()));
        fields.add(String.valueOf(to.getX()));
        fields.add(String.valueOf(to.getY()));
        fields.add(Csv.name(reason));
        fields.add(Csv.name(role));
        fields.add("-1");
        fields.add("-1");
        return String.join(",", fields);
    }

    static String diedRow(int frame, int unitId, Position position, UnitRole role, double sporeDistance,
                          boolean parked) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(frame));
        fields.add("DIED");
        fields.add(String.valueOf(unitId));
        fields.add(String.valueOf(position.getX()));
        fields.add(String.valueOf(position.getY()));
        fields.add("-1");
        fields.add("-1");
        fields.add("-1");
        fields.add("-1");
        fields.add("NONE");
        fields.add(Csv.name(role));
        fields.add(sporeDistance < 0 ? "-1" : Csv.format(sporeDistance));
        fields.add(parked ? "1" : "0");
        return String.join(",", fields);
    }
}
