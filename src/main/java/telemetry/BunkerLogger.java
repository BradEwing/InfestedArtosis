package telemetry;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import info.tracking.ObservedUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Writes telemetry_bunker.csv: the rows about enemy Bunkers, told apart by row_type.
 *
 * <p>BUNKER_ADVANCE: one row per repeat-advance gate decision about a squad and a Bunker in range. event is HELD or
 * ALLOWED and reason names why, see {@link BunkerAdvanceReason}. own_strength is the squad's priced strength,
 * bunker_price the enemy strength the recorded loss was priced against, ratio their quotient and release_ratio the
 * ratio at which the record ends; price, ratio and release_ratio are blank while no loss is on record. ling_count is
 * the Zerglings in the squad, bunker_id, bunker_x, bunker_y and bunker_hp the Bunker, blank when none is known. A
 * squad's row repeats only when its reason or Bunker changes or {@link #ADVANCE_REPEAT_FRAMES} pass.
 *
 * <p>BUNKER_ENGAGEMENT: one row per closed engagement of ours at a Bunker, see {@link BunkerEngagements}.
 * start_frame and end_frame bound it, our_lost counts our units that died in it, lings_lost the Zerglings among
 * them, enemy_lost the enemy units that are not buildings that died in it, hp_start and hp_end the Bunker's hit
 * points, and broken is 1 when the Bunker died in it.
 *
 * <p>BUNKER_HOLD: one row each time the Bunker hold of the enemy natural or main starts (event HOLD_START, reason
 * NATURAL, MAIN or NATURAL+MAIN) or ends (event HOLD_END, reason BROKEN or CLEARED).
 *
 * <p>BUNKER_ECON: one row per change of a Bunker stance, see the Drone round it opens. event is STANCE_START,
 * ROUND_OPEN, ROUND_CLOSE or STANCE_END, stance_id numbers the game's stances from 1, drones counts Drones hatched or
 * in an egg, workers the workers gathering, extra_planned the Drones the stance's rounds were set to add so far and
 * extra_made the Drones its closed rounds added so far.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class BunkerLogger implements BunkerSink {

    static final String FILE = "telemetry_bunker.csv";

    static final String[] COLUMNS = {
        "game_id", "frame", "row_type", "event", "squad_id", "reason", "own_strength", "bunker_price", "ratio",
        "release_ratio", "ling_count", "bunker_id", "bunker_x", "bunker_y", "bunker_hp", "engagement_id",
        "start_frame", "end_frame", "our_lost", "lings_lost", "enemy_lost", "hp_start", "hp_end", "broken",
        "stance_id", "drones", "workers", "extra_planned", "extra_made"
    };

    static final String HEADER = String.join(",", COLUMNS);

    /**
     * Frames between two identical BUNKER_ADVANCE rows of a squad.
     */
    static final int ADVANCE_REPEAT_FRAMES = 240;

    private static final int SAMPLE_INTERVAL_FRAMES = 4;
    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private final Game game;
    private final GameState gameState;
    private final String gameId;
    private final TelemetryWriter writer;
    private final BunkerEngagements engagements = new BunkerEngagements();
    private final Map<String, AdvanceKey> lastAdvance = new HashMap<>();

    private boolean disabled;

    private static final class AdvanceKey {
        private final BunkerAdvanceReason reason;
        private final int bunkerId;
        private final int frame;

        private AdvanceKey(BunkerAdvanceReason reason, int bunkerId, int frame) {
            this.reason = reason;
            this.bunkerId = bunkerId;
            this.frame = frame;
        }
    }

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
            int frame = game.getFrameCount();
            if (frame % SAMPLE_INTERVAL_FRAMES == 0) {
                writeEngagements(engagements.onSample(frame, livingBunkers(), ourUnits()));
            }
            if (frame % FLUSH_INTERVAL_FRAMES == 0) {
                writer.flush();
            }
        } catch (RuntimeException e) {
            disable();
        }
    }

    public void onUnitDestroy(Unit unit) {
        if (disabled) {
            return;
        }

        try {
            UnitType type = unit.getType();
            if (unit.getPlayer() != null && unit.getPlayer().getID() == game.self().getID()) {
                if (isGroundCombat(type)) {
                    engagements.onOurDeath(unit.getPosition(), type == UnitType.Zerg_Zergling);
                }
            } else if (unit.getPlayer() != null && unit.getPlayer().isEnemy(game.self()) && !type.isBuilding()) {
                engagements.onEnemyDeath(unit.getPosition());
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
            writeEngagements(engagements.finish(game.getFrameCount()));
            writer.flush();
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onAdvance(BunkerAdvanceEvent event) {
        if (disabled) {
            return;
        }

        try {
            AdvanceKey last = lastAdvance.get(event.getSquadId());
            if (last != null && last.reason == event.getReason() && last.bunkerId == event.getBunker().getId()
                    && event.getFrame() - last.frame < ADVANCE_REPEAT_FRAMES) {
                return;
            }
            lastAdvance.put(event.getSquadId(),
                    new AdvanceKey(event.getReason(), event.getBunker().getId(), event.getFrame()));
            writer.append(advanceRow(gameId, event));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onHold(int frame, String event, String reason) {
        if (disabled) {
            return;
        }

        try {
            writer.append(holdRow(gameId, frame, event, reason));
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

    private void writeEngagements(List<BunkerEngagements.Closed> closed) {
        for (BunkerEngagements.Closed engagement : closed) {
            writer.append(engagementRow(gameId, engagement));
        }
    }

    private List<BunkerEngagements.BunkerSample> livingBunkers() {
        List<BunkerEngagements.BunkerSample> bunkers = new ArrayList<>();
        for (ObservedUnit ou : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            if (ou.getUnitType() != UnitType.Terran_Bunker || !ou.isCompleted()) {
                continue;
            }
            bunkers.add(new BunkerEngagements.BunkerSample(ou.getUnit().getID(),
                    ou.getCurrentOrLastKnownPosition(), ou.getLastKnownHitPoints()));
        }
        return bunkers;
    }

    private List<BunkerEngagements.OurUnit> ourUnits() {
        List<BunkerEngagements.OurUnit> units = new ArrayList<>();
        for (Unit unit : game.self().getUnits()) {
            if (isGroundCombat(unit.getType())) {
                units.add(new BunkerEngagements.OurUnit(unit.getPosition()));
            }
        }
        return units;
    }

    private static boolean isGroundCombat(UnitType type) {
        return type.canAttack() && !type.isWorker() && !type.isBuilding() && !type.isFlyer();
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

    private static String known(int value) {
        return value < 0 ? "" : String.valueOf(value);
    }

    /**
     * Builds a BUNKER_ADVANCE row in {@link #HEADER} order.
     *
     * @return the row
     */
    static String advanceRow(String gameId, BunkerAdvanceEvent event) {
        String[] cells = blankRow(gameId, event.getFrame(), "BUNKER_ADVANCE",
                event.getReason().isHeld() ? "HELD" : "ALLOWED");
        set(cells, "squad_id", Csv.sanitize(event.getSquadId()));
        set(cells, "reason", event.getReason().name());
        set(cells, "own_strength", Csv.format(event.getOwnStrength()));
        if (event.getBunkerPrice() > 0) {
            set(cells, "bunker_price", Csv.format(event.getBunkerPrice()));
            set(cells, "ratio", Csv.format(event.getOwnStrength() / event.getBunkerPrice()));
            set(cells, "release_ratio", Csv.format(event.getReleaseRatio()));
        }
        set(cells, "ling_count", String.valueOf(event.getLingCount()));
        set(cells, "bunker_id", known(event.getBunker().getId()));
        set(cells, "bunker_x", known(event.getBunker().getX()));
        set(cells, "bunker_y", known(event.getBunker().getY()));
        set(cells, "bunker_hp", known(event.getBunker().getHitPoints()));
        return String.join(",", cells);
    }

    /**
     * Builds a BUNKER_ENGAGEMENT row in {@link #HEADER} order.
     *
     * @return the row
     */
    static String engagementRow(String gameId, BunkerEngagements.Closed engagement) {
        String[] cells = blankRow(gameId, engagement.getEndFrame(), "BUNKER_ENGAGEMENT", "CLOSED");
        set(cells, "bunker_id", String.valueOf(engagement.getBunkerId()));
        set(cells, "bunker_x", String.valueOf(engagement.getBunker().getX()));
        set(cells, "bunker_y", String.valueOf(engagement.getBunker().getY()));
        set(cells, "engagement_id", String.valueOf(engagement.getId()));
        set(cells, "start_frame", String.valueOf(engagement.getStartFrame()));
        set(cells, "end_frame", String.valueOf(engagement.getEndFrame()));
        set(cells, "our_lost", String.valueOf(engagement.getOurLost()));
        set(cells, "lings_lost", String.valueOf(engagement.getLingsLost()));
        set(cells, "enemy_lost", String.valueOf(engagement.getEnemyLost()));
        set(cells, "hp_start", String.valueOf(engagement.getHitPointsStart()));
        set(cells, "hp_end", String.valueOf(engagement.getHitPointsEnd()));
        set(cells, "broken", engagement.isBroken() ? "1" : "0");
        return String.join(",", cells);
    }

    /**
     * Builds a BUNKER_HOLD row in {@link #HEADER} order.
     *
     * @return the row
     */
    static String holdRow(String gameId, int frame, String event, String reason) {
        String[] cells = blankRow(gameId, frame, "BUNKER_HOLD", event);
        set(cells, "reason", Csv.sanitize(reason));
        return String.join(",", cells);
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
