package telemetry;

import bwapi.Game;
import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Writes telemetry_speed_parity.csv: the speed parity gate's holds and the frames the enemy's speed upgrades were
 * first inferred.
 *
 * <p>A GATE row is written when the gate's term for a squad changes. suppressed_by is SPEED_PARITY when the squad
 * is held home because the enemy has, or plausibly has, Metabolic Boost and we do not, and LING_UPGRADE when the
 * move out threshold was raised for a ling-heavy squad with no ling upgrade. applied is false when the gate switch
 * is off and the term did not change the decision, so the off arm stays measurable. our_speed and enemy_speed name
 * the two sides' state, squad_supply and move_out_threshold the squad's strength against the bar it was held to, and
 * base_threshold the bar without the gate.
 *
 * <p>A GATE_END row is written when a squad's term ends, with the ended term in suppressed_by.
 *
 * <p>An ENEMY_SPEED row is written for each first of the game the enemy speed tracker reports for an upgrade, with
 * the upgrade and the event in evidence: PLAUSIBLE when the frame reaches the upgrade's earliest finish, SEEN when a
 * run made it SEEN, and EARLY_RUN for the first run refused because it came before the earliest finish. bound_frame
 * is the earliest finish on that frame and bound_anchor what it rests on. A SEEN or EARLY_RUN row also carries the
 * run: measured_speed is the distance one unit covered over it per frame, top_speed its type's unupgraded top speed,
 * and run_frames the frames observed; the run's last frame is the row's frame. Every other column is NONE or -1.
 *
 * <p>An OWN_SPEED row is written the first frame our Metabolic Boost is finished, with evidence FINISHED.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class SpeedParityLogger implements SpeedParitySink {

    static final String FILE = "telemetry_speed_parity.csv";

    static final String HEADER = "game_id,frame,event,squad_id,suppressed_by,applied,our_speed,enemy_speed,upgrade,"
            + "evidence,squad_supply,move_out_threshold,base_threshold,zerglings,measured_speed,top_speed,run_frames,"
            + "bound_frame,bound_anchor";

    static final String EVENT_GATE = "GATE";
    static final String EVENT_GATE_END = "GATE_END";
    static final String EVENT_ENEMY_SPEED = "ENEMY_SPEED";
    static final String EVENT_OWN_SPEED = "OWN_SPEED";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;
    private static final String NONE = "NONE";
    private static final String NO_RUN = String.join(",", "-1", "-1", "-1", "-1", NONE);

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
                    writer.append(gateEndRow(gameId, frame, gate.getSquadId(), ended, gate));
                }
                return;
            }
            String previous = terms.put(gate.getSquadId(), gate.getTerm());
            if (gate.getTerm().equals(previous)) {
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
                String.valueOf(gate.isApplied()), gate.getOurSpeed(), gate.getEnemySpeed(), NONE, NONE,
                String.valueOf(gate.getSupply()), String.valueOf(gate.getThreshold()),
                String.valueOf(gate.getBaseThreshold()), String.valueOf(gate.getZerglings()), NO_RUN);
    }

    /**
     * Builds a GATE_END row in {@link #HEADER} order: the squad's term ended, named in suppressed_by, with the
     * speed states of the frame it ended on.
     */
    static String gateEndRow(String gameId, int frame, String squadId, String endedTerm, SpeedParityGate gate) {
        return String.join(",", gameId, String.valueOf(frame), EVENT_GATE_END, squadId, endedTerm, "false",
                gate.getOurSpeed(), gate.getEnemySpeed(), NONE, NONE, String.valueOf(NOT_EVALUATED),
                String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), NO_RUN);
    }

    /**
     * Builds an ENEMY_SPEED row in {@link #HEADER} order.
     */
    static String enemySpeedRow(String gameId, int frame, EnemySpeedTracker.Report report) {
        return String.join(",", gameId, String.valueOf(frame), EVENT_ENEMY_SPEED, NONE, NONE, NONE, NONE, NONE,
                Csv.name(report.getUpgrade()), report.getEvent().name(), String.valueOf(NOT_EVALUATED),
                String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED),
                speed(report.getMeasuredSpeed()), speed(report.getTopSpeed()), String.valueOf(report.getRunFrames()),
                String.valueOf(report.getBoundFrame()), report.getAnchor().name());
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
                String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED), NO_RUN);
    }
}
