package telemetry;

import bwapi.Game;
import bwapi.Position;
import bwapi.UnitType;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes telemetry_fixed_fire.csv: the cooldowns on targets inside fixed fire, the targets they skip, and the
 * points Lurkers hold out of that fire.
 *
 * <p>Fixed fire is the ground an enemy fires on from where it stands: a building, a sieged tank or a Lurker.
 * COOLDOWN_START is written when one of our units is hurt inside such a zone that was not already cooling; the unit
 * columns name the unit hurt and where it stood. COOLDOWN_SKIP is written once per attacker and target per cooldown
 * when a fighter's target is skipped for standing in a cooling zone; the unit columns name the attacker, the target
 * columns the target, and point_x and point_y where the target stood. LURKER_HOLD is written when a Lurker is given
 * a point to hold out of fire, with the point in point_x and point_y and what sent it out in reason: HIT when it was
 * hurt inside a sieged tank's reach, RETREAT when its squad retreated with it inside fixed fire, MOVED when the fire
 * moved onto the point it held, and COOLDOWN when every target it had stood in cooling fire. LURKER_HOLD_RELEASE
 * is written when it lets go of that point, with the point it held and the reason: COMMIT when its squad commits to
 * the fight, CLEAR when no fixed fire is near the point any more, and STATUS when its squad left FIGHT and RETREAT.
 *
 * <p>The zone columns describe the zone the row is about: its type, None for a hurt mark, its centre and its reach.
 * A column that does not apply to a row is -1, or NONE for a type or reason.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class FixedFireLogger implements FixedFireSink {

    static final String FILE = "telemetry_fixed_fire.csv";

    static final String HEADER = "game_id,frame,event,unit_id,unit_type,unit_x,unit_y,point_x,point_y,zone_type,"
            + "zone_x,zone_y,zone_reach,target_id,target_type,reason";

    static final String EVENT_COOLDOWN_START = "COOLDOWN_START";
    static final String EVENT_COOLDOWN_SKIP = "COOLDOWN_SKIP";
    static final String EVENT_LURKER_HOLD = "LURKER_HOLD";
    static final String EVENT_LURKER_HOLD_RELEASE = "LURKER_HOLD_RELEASE";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;
    private static final String NONE = "NONE";

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public FixedFireLogger(Game game, String gameId) {
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
    public void onCooldownStarted(int frame, int unitId, UnitType unitType, Position position,
                                  StaticDefenseZone zone) {
        append(row(gameId, frame, EVENT_COOLDOWN_START, unitCells(unitId, unitType, position), null, zone,
                targetCells(NOT_EVALUATED, null), null));
    }

    @Override
    public void onTargetSkipped(int frame, int attackerId, UnitType attackerType, Position attackerPosition,
                                int targetId, UnitType targetType, Position targetPosition,
                                StaticDefenseZone zone) {
        append(row(gameId, frame, EVENT_COOLDOWN_SKIP, unitCells(attackerId, attackerType, attackerPosition),
                targetPosition, zone, targetCells(targetId, targetType), null));
    }

    @Override
    public void onLurkerHold(int frame, int lurkerId, Position position, Position holdPoint, StaticDefenseZone zone,
                             String reason) {
        append(row(gameId, frame, EVENT_LURKER_HOLD, unitCells(lurkerId, UnitType.Zerg_Lurker, position), holdPoint,
                zone, targetCells(NOT_EVALUATED, null), reason));
    }

    @Override
    public void onLurkerHoldReleased(int frame, int lurkerId, Position position, Position holdPoint,
                                     String reason) {
        append(row(gameId, frame, EVENT_LURKER_HOLD_RELEASE, unitCells(lurkerId, UnitType.Zerg_Lurker, position),
                holdPoint, null, targetCells(NOT_EVALUATED, null), reason));
    }

    private void append(String row) {
        if (disabled) {
            return;
        }

        try {
            writer.append(row);
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        FixedFireTelemetry.clear();
    }

    /**
     * Builds one CSV row in {@link #HEADER} order.
     *
     * @param unit the cells from unit_id through unit_y, see {@link #unitCells}
     * @param point the position for point_x and point_y, or null
     * @param zone the zone for the zone columns, or null
     * @param target the target_id and target_type cells, see {@link #targetCells}
     * @param reason the reason, or null
     * @return the row
     */
    static String row(String gameId, int frame, String event, List<String> unit, Position point,
                      StaticDefenseZone zone, List<String> target, String reason) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(frame));
        fields.add(event);
        fields.addAll(unit);
        fields.addAll(positionCells(point));
        fields.add(zone == null ? NONE : Csv.name(zone.getStructure()));
        fields.addAll(positionCells(zone == null ? null : zone.getCenter()));
        fields.add(String.valueOf(zone == null ? NOT_EVALUATED : zone.getReach()));
        fields.addAll(target);
        fields.add(reason == null ? NONE : reason);
        return String.join(",", fields);
    }

    /**
     * Builds the cells from unit_id through unit_y.
     */
    static List<String> unitCells(int unitId, UnitType unitType, Position position) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(unitId));
        fields.add(Csv.name(unitType));
        fields.addAll(positionCells(position));
        return fields;
    }

    /**
     * Builds the target_id and target_type cells.
     */
    static List<String> targetCells(int targetId, UnitType targetType) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(targetId));
        fields.add(Csv.name(targetType));
        return fields;
    }

    private static List<String> positionCells(Position position) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getX()));
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getY()));
        return fields;
    }
}
