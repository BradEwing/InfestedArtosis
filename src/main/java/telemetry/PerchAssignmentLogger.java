package telemetry;

import bwapi.Game;
import bwapi.Position;
import bwapi.UnitType;
import info.GameState;
import info.map.PerchCalculator;
import unit.managed.ManagedUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every perch assignment: where the scout was when it was given a perch, where the perch is,
 * and what the perch was meant to watch.
 *
 * <p>transit_px is the distance the scout is being asked to fly. It is the column that says whether
 * perch selection is sending overlords across the map, so it is written straight rather than derived
 * downstream from the coordinates.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class PerchAssignmentLogger implements PerchAssignmentSink {

    static final String FILE = "telemetry_perch_assignments.csv";

    static final String HEADER = "game_id,frame,unit_id,unit_type,scout_x,scout_y,perch_x,perch_y,transit_px,"
            + "transit_budget_px,watch_x,watch_y,watch_distance_px,watches_target,perch_tiles,perch_clearance_tiles";

    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private final Game game;
    private final GameState gameState;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public PerchAssignmentLogger(Game game, GameState gameState, String gameId) {
        this.game = game;
        this.gameState = gameState;
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
    public void onPerchAssigned(ManagedUnit scout, Position perch, Position watchTarget) {
        if (disabled) {
            return;
        }

        try {
            writer.append(row(scout, perch, watchTarget));
        } catch (Exception e) {
            disabled = true;
        }
    }

    private String row(ManagedUnit scout, Position perch, Position watchTarget) {
        Position from = scout.getPosition();
        double watchDistance = perch.getDistance(watchTarget);
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(game.getFrameCount()));
        fields.add(String.valueOf(scout.getUnitID()));
        fields.add(Csv.name(scout.getUnitType()));
        fields.add(String.valueOf(from.getX()));
        fields.add(String.valueOf(from.getY()));
        fields.add(String.valueOf(perch.getX()));
        fields.add(String.valueOf(perch.getY()));
        fields.add(Csv.format(from.getDistance(perch)));
        fields.add(String.valueOf(PerchCalculator.transitPixels(scout.getUnitType())));
        fields.add(String.valueOf(watchTarget.getX()));
        fields.add(String.valueOf(watchTarget.getY()));
        fields.add(Csv.format(watchDistance));
        fields.add(watchDistance <= UnitType.Zerg_Overlord.sightRange() ? "1" : "0");
        fields.add(String.valueOf(gameState.getGameMap().getPerchTiles().size()));
        fields.add(String.valueOf(gameState.getGameMap().getPerchClearanceTiles()));
        return String.join(",", fields);
    }
}
