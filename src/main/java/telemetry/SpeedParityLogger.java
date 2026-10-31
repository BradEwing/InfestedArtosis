package telemetry;

import bwapi.Game;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker;
import info.tracking.SpeedRunDetector;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes telemetry_speed_parity.csv: the speed parity gate's holds, the enemy speed inferences, and the speed
 * detector's calibration on our own units.
 *
 * <p>A GATE row is written when the gate's term for a squad, or whether it was applied, changes. suppressed_by is
 * SPEED_PARITY when the squad is held home because the enemy was seen with Metabolic Boost and we do not have it, and
 * LING_UPGRADE when the move out threshold was raised for a ling-heavy squad with no ling upgrade. applied is false
 * when the gate switch is off and the term did not change the decision, so the off arm stays measurable. our_speed
 * and enemy_speed name the two sides' state, squad_supply and move_out_threshold the squad's strength against the bar
 * it was held to, and base_threshold the bar without the gate. upgrade is Metabolic Boost, evidence the enemy's
 * evidence for it, and bound_frame and bound_anchor its earliest finish and what that rests on, on the row's frame.
 *
 * <p>A GATE_END row is written when a squad's term ends, with the ended term in suppressed_by and the enemy's
 * Metabolic Boost evidence and bound on the frame it ended.
 *
 * <p>An ENEMY_SPEED row, side ENEMY, is written for each report of the enemy speed tracker, with the upgrade and the
 * event in evidence: PLAUSIBLE the first frame reaching the upgrade's earliest finish and every later frame its
 * anchor changed or it moved 120 frames or more from the last one reported, so a bound_frame after the row's frame
 * means the bound moved past it; SEEN when a run made it SEEN; and EARLY_RUN for the first run refused because it
 * ended before the earliest finish. bound_frame is the earliest finish on that frame and bound_anchor what it rests
 * on. A SEEN or EARLY_RUN row also carries the run, two passing windows of one unit, see {@link SpeedRunDetector}:
 * measured_speed is the distance that unit covered per frame over the slower of the two, top_speed its type's
 * unupgraded top speed, and run_frames the frames from the start of the first window to the end of the second; its
 * last frame is the row's frame.
 *
 * <p>A SELF_SPEED row, side SELF, is the same detector run on our own units: EARLY_RUN for the first run that ended
 * before our upgrade finished, with bound_frame -1, and SEEN for the first one that started at or after it, with
 * bound_frame our finish.
 *
 * <p>A SPEED_DIAG row is written for each side and unit type when the game ends, and also on frame 9000 and on the
 * frame of the first ENEMY SEEN, for games that never end cleanly; those rows are cumulative and told apart from the
 * game-end ones by their frame. One is also written for SELF and the upgraded type when our upgrade finishes; that
 * tally then starts afresh, so the SELF rows after it start at our finish, as from_frame says. upgrade
 * names the type's upgrade, top_speed its unupgraded top speed and run_frames the deciding window. from_frame is the
 * frame the tally began, longest_run the most consecutive sightings of one unit whose deciding window passed, and
 * max_speed_8, max_speed_16 and max_speed_24 the fastest window of that many frames as a multiple of the top speed.
 * windows counts the deciding windows measured, velocity_frames the sightings whose velocity was above the cut, and
 * not_moving_frames the sightings whose moving flag was false while the unit had moved since its last sighting.
 * clears_gap counts the units started afresh after going unseen, and clears_jump those started afresh after a jump.
 * steps_0 to steps_over_2 count the steps between two sightings by their distance per frame as a multiple of the top
 * speed: none, up to 1, 1.25, 1.5 and 2, and above 2. max_discounted_8 is the fastest deciding window less its longest
 * step's excess over the top speed, and max_run_8 the fastest run, the slower of its two deciding windows, which is
 * what SEEN compares with the type's margin; both as multiples of the top speed.
 *
 * <p>An OWN_SPEED row, side SELF, is written the first frame our Metabolic Boost is finished, with evidence FINISHED.
 *
 * <p>Every column a row does not fill is NONE or -1. Constructed only when combat telemetry is enabled.
 */
public class SpeedParityLogger implements SpeedParitySink {

    static final String FILE = "telemetry_speed_parity.csv";

    static final String HEADER = "game_id,frame,event,squad_id,suppressed_by,applied,our_speed,enemy_speed,upgrade,"
            + "evidence,squad_supply,move_out_threshold,base_threshold,zerglings,measured_speed,top_speed,run_frames,"
            + "bound_frame,bound_anchor,side,longest_run,max_speed_8,max_speed_16,max_speed_24,windows,velocity_frames,"
            + "not_moving_frames,from_frame,clears_gap,clears_jump,steps_0,steps_1,steps_1_25,steps_1_5,steps_2,"
            + "steps_over_2,max_discounted_8,max_run_8";

    static final String EVENT_GATE = "GATE";
    static final String EVENT_GATE_END = "GATE_END";
    static final String EVENT_ENEMY_SPEED = "ENEMY_SPEED";
    static final String EVENT_OWN_SPEED = "OWN_SPEED";
    static final String EVENT_SELF_SPEED = "SELF_SPEED";
    static final String EVENT_SPEED_DIAG = "SPEED_DIAG";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;
    private static final String NONE = "NONE";
    private static final String NO_WINDOW = String.join(",", "-1", "-1", "-1");
    private static final String NO_RUN = String.join(",", NO_WINDOW, "-1", NONE);
    private static final int DIAGNOSTIC_COLUMNS = 18;
    private static final String NO_DIAGNOSTIC = String.join(",",
            Collections.nCopies(DIAGNOSTIC_COLUMNS, String.valueOf(NOT_EVALUATED)));

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;
    private final Map<String, String> terms = new HashMap<>();

    private boolean disabled;

    public SpeedParityLogger(Game game, String gameId) {
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
    public void onGateRead(int frame, SpeedParityGate gate) {
        if (disabled) {
            return;
        }

        try {
            if (NONE.equals(gate.getTerm())) {
                String ended = terms.remove(gate.getSquadId());
                if (ended != null) {
                    writer.append(gateEndRow(gameId, frame, gate.getSquadId(), ended.substring(0, ended.indexOf(':')),
                            gate));
                }
                return;
            }
            String key = gate.getTerm() + ":" + gate.isApplied();
            String previous = terms.put(gate.getSquadId(), key);
            if (key.equals(previous)) {
                return;
            }
            writer.append(gateRow(gameId, frame, gate));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onEnemySpeed(int frame, EnemySpeedTracker.Report report) {
        if (disabled) {
            return;
        }

        try {
            writer.append(enemySpeedRow(gameId, frame, report));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onSelfSpeed(int frame, EnemySpeedTracker.Report report) {
        if (disabled) {
            return;
        }

        try {
            writer.append(selfSpeedRow(gameId, frame, report));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onSpeedDiagnostic(int frame, Side side, SpeedRunDetector.Diagnostic diagnostic) {
        if (disabled) {
            return;
        }

        try {
            writer.append(diagnosticRow(gameId, frame, side, diagnostic));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onOwnSpeedFinished(int frame) {
        if (disabled) {
            return;
        }

        try {
            writer.append(ownSpeedRow(gameId, frame));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        SpeedParityTelemetry.clear();
    }

    /**
     * Builds a GATE row in {@link #HEADER} order.
     */
    static String gateRow(String gameId, int frame, SpeedParityGate gate) {
        return String.join(",", gameId, String.valueOf(frame), EVENT_GATE, gate.getSquadId(), gate.getTerm(),
                String.valueOf(gate.isApplied()), gate.getOurSpeed(), gate.getEnemySpeed(),
                Csv.name(UpgradeType.Metabolic_Boost), gate.getEnemySpeed(), String.valueOf(gate.getSupply()),
                String.valueOf(gate.getThreshold()), String.valueOf(gate.getBaseThreshold()),
                String.valueOf(gate.getZerglings()), NO_WINDOW, String.valueOf(gate.getEnemyBound()),
                gate.getEnemyAnchor(), NONE, NO_DIAGNOSTIC);
    }

    /**
     * Builds a GATE_END row in {@link #HEADER} order: the squad's term ended, named in suppressed_by, with the
     * speed states of the frame it ended on.
     */
    static String gateEndRow(String gameId, int frame, String squadId, String endedTerm, SpeedParityGate gate) {
        return String.join(",", gameId, String.valueOf(frame), EVENT_GATE_END, squadId, endedTerm, "false",
                gate.getOurSpeed(), gate.getEnemySpeed(), Csv.name(UpgradeType.Metabolic_Boost),
                gate.getEnemySpeed(), String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED),
                String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), NO_WINDOW,
                String.valueOf(gate.getEnemyBound()), gate.getEnemyAnchor(), NONE, NO_DIAGNOSTIC);
    }

    /**
     * Builds an ENEMY_SPEED row in {@link #HEADER} order.
     */
    static String enemySpeedRow(String gameId, int frame, EnemySpeedTracker.Report report) {
        return windowRow(gameId, frame, EVENT_ENEMY_SPEED, Side.ENEMY, report);
    }

    /**
     * Builds a SELF_SPEED row in {@link #HEADER} order.
     */
    static String selfSpeedRow(String gameId, int frame, EnemySpeedTracker.Report report) {
        return windowRow(gameId, frame, EVENT_SELF_SPEED, Side.SELF, report);
    }

    private static String windowRow(String gameId, int frame, String event, Side side,
                                    EnemySpeedTracker.Report report) {
        return String.join(",", gameId, String.valueOf(frame), event, NONE, NONE, NONE, NONE, NONE,
                Csv.name(report.getUpgrade()), report.getEvent().name(), String.valueOf(NOT_EVALUATED),
                String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED),
                speed(report.getMeasuredSpeed()), speed(report.getTopSpeed()), String.valueOf(report.getRunFrames()),
                String.valueOf(report.getBoundFrame()), Csv.name(report.getAnchor()), side.name(), NO_DIAGNOSTIC);
    }

    /**
     * Builds a SPEED_DIAG row in {@link #HEADER} order.
     */
    static String diagnosticRow(String gameId, int frame, Side side, SpeedRunDetector.Diagnostic diagnostic) {
        UnitType type = diagnostic.getType();
        return String.join(",", gameId, String.valueOf(frame), EVENT_SPEED_DIAG, NONE, NONE, NONE, NONE, NONE,
                Csv.name(EnemySpeedTracker.upgradeFor(type)), NONE, String.valueOf(NOT_EVALUATED),
                String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED),
                String.valueOf(NOT_EVALUATED), speed(type.topSpeed()), String.valueOf(SpeedRunDetector.WINDOW_FRAMES),
                String.valueOf(NOT_EVALUATED), NONE, side.name(), String.valueOf(diagnostic.getLongestRun()),
                speed(diagnostic.getWindowSpeed()), speed(diagnostic.getMidWindowSpeed()),
                speed(diagnostic.getLongWindowSpeed()), String.valueOf(diagnostic.getWindows()),
                String.valueOf(diagnostic.getVelocityFrames()), String.valueOf(diagnostic.getNotMovingFrames()),
                String.valueOf(diagnostic.getFromFrame()), String.valueOf(diagnostic.getGapClears()),
                String.valueOf(diagnostic.getJumpClears()),
                Arrays.stream(diagnostic.getSteps()).mapToObj(String::valueOf).collect(Collectors.joining(",")),
                speed(diagnostic.getDiscountedWindowSpeed()), speed(diagnostic.getRunSpeed()));
    }

    private static String speed(double pixelsPerFrame) {
        return pixelsPerFrame < 0 ? String.valueOf(NOT_EVALUATED) : String.format(Locale.ROOT, "%.3f", pixelsPerFrame);
    }

    /**
     * Builds an OWN_SPEED row in {@link #HEADER} order.
     */
    static String ownSpeedRow(String gameId, int frame) {
        return String.join(",", gameId, String.valueOf(frame), EVENT_OWN_SPEED, NONE, NONE, NONE, NONE, NONE,
                Csv.name(UpgradeType.Metabolic_Boost), "FINISHED", String.valueOf(NOT_EVALUATED),
                String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), NO_RUN,
                Side.SELF.name(), NO_DIAGNOSTIC);
    }
}
