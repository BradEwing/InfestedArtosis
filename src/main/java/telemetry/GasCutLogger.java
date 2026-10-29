package telemetry;

import bwapi.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes telemetry_gas_cut.csv: one row each time a build that cuts gas after Metabolic Boost engages the cut, and
 * one row each time a queued plan costing gas lifts it.
 *
 * <p>event is CUT or DEMAND. applied is 1 when the cut decided the worker split and 0 when its switch was off, so the
 * off arm records what the cut would have done. plan_type and plan_gas name the queued plan that lifted the cut and
 * are NONE and 0 on a CUT row. available_gas is the gas mined and unreserved and gas_workers the drones gathering gas
 * when the row was written.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class GasCutLogger implements GasCutSink {

    static final String FILE = "telemetry_gas_cut.csv";

    static final String HEADER = "game_id,frame,event,applied,plan_type,plan_gas,available_gas,gas_workers";

    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public GasCutLogger(Game game, String gameId) {
        this(game, gameId, new TelemetryWriter(FILE, HEADER));
    }

    GasCutLogger(Game game, String gameId, TelemetryWriter writer) {
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
    public void onGasCut(int frame, String event, boolean applied, String planType, int planGas, int availableGas,
                         int gasWorkers) {
        if (disabled) {
            return;
        }

        try {
            writer.append(row(gameId, frame, event, applied, planType, planGas, availableGas, gasWorkers));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        GasCutTelemetry.clear();
    }

    /**
     * Builds one CSV row in {@link #HEADER} order.
     *
     * @return the row
     */
    static String row(String gameId, int frame, String event, boolean applied, String planType, int planGas,
                      int availableGas, int gasWorkers) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(frame));
        fields.add(Csv.sanitize(event));
        fields.add(applied ? "1" : "0");
        fields.add(Csv.name(planType));
        fields.add(String.valueOf(planGas));
        fields.add(String.valueOf(availableGas));
        fields.add(String.valueOf(gasWorkers));
        return String.join(",", fields);
    }
}
