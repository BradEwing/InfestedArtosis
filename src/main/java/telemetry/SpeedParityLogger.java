package telemetry;

import bwapi.Game;
import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker;

import java.util.HashMap;
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
 * <p>An ENEMY_SPEED row is written when the evidence for an enemy upgrade first reaches PLAUSIBLE or SEEN, with the
 * upgrade and the level in evidence. Only those columns apply to it; the rest are NONE or -1.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class SpeedParityLogger implements SpeedParitySink {

    static final String FILE = "telemetry_speed_parity.csv";

    static final String HEADER = "game_id,frame,event,squad_id,suppressed_by,applied,our_speed,enemy_speed,upgrade,"
            + "evidence,squad_supply,move_out_threshold,base_threshold,zerglings";

    static final String EVENT_GATE = "GATE";
    static final String EVENT_ENEMY_SPEED = "ENEMY_SPEED";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;
    private static final String NONE = "NONE";

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
                terms.remove(gate.getSquadId());
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
    public void onEnemySpeed(int frame, UpgradeType upgrade, EnemySpeedTracker.Evidence evidence) {
        if (disabled) {
            return;
        }

        try {
            writer.append(enemySpeedRow(gameId, frame, upgrade, evidence));
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
                String.valueOf(gate.getBaseThreshold()), String.valueOf(gate.getZerglings()));
    }

    /**
     * Builds an ENEMY_SPEED row in {@link #HEADER} order.
     */
    static String enemySpeedRow(String gameId, int frame, UpgradeType upgrade, EnemySpeedTracker.Evidence evidence) {
        return String.join(",", gameId, String.valueOf(frame), EVENT_ENEMY_SPEED, NONE, NONE, NONE, NONE, NONE,
                Csv.name(upgrade), evidence.name(), String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED),
                String.valueOf(NOT_EVALUATED), String.valueOf(NOT_EVALUATED));
    }
}
