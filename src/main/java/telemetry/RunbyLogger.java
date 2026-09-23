package telemetry;

import bwapi.Game;
import bwapi.Position;
import unit.squad.RunbyEvaluator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes telemetry_runby.csv: one TICK row per runby decision tick, and one ENTRY_CHECK row whenever the runby
 * entry verdict of a containing squad changes.
 *
 * <p>A TICK row carries the phase, where the squad is seeking workers and why, the abort tally while the abort
 * window is open, and what the tick cost and earned. hp_lost is the hit points the squad's lings lost since the
 * previous tick, dead lings included. hp_lost_non_worker_in_reach is the part of it lost by lings standing inside
 * a non-worker's reach at the tick; no damage source is recorded, so it is a proxy for damage from non-workers.
 * workers_killed and buildings_killed are cumulative over the episode.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class RunbyLogger implements RunbySink {

    static final String FILE = "telemetry_runby.csv";

    static final String HEADER = "game_id,frame,squad_id,event,verdict,phase,goal_type,seek_x,seek_y,anchor_x,anchor_y,"
            + "abort_window_open,abort_enemy_tally,abort_our_tally,in_base_area,lings,workers_visible,exposed_lings,"
            + "hp_lost,hp_lost_non_worker_in_reach,winnable,bases_under_attack,workers_killed,buildings_killed";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;
    private final Map<String, RunbyEvaluator.EntryVerdict> lastVerdict = new HashMap<>();

    private boolean disabled;

    public RunbyLogger(Game game, String gameId) {
        this(game, gameId, new TelemetryWriter(FILE, HEADER));
    }

    RunbyLogger(Game game, String gameId, TelemetryWriter writer) {
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
    public void onTick(RunbyTick tick) {
        if (disabled) {
            return;
        }

        try {
            if (tick.getEvent() == RunbyTick.Event.ENTRY_CHECK) {
                if (tick.getVerdict() == lastVerdict.get(tick.getSquadId())) {
                    return;
                }
                lastVerdict.put(tick.getSquadId(), tick.getVerdict());
            }
            writer.append(row(gameId, tick));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        lastVerdict.clear();
        RunbyTelemetry.clear();
    }

    /**
     * Builds one CSV row in {@link #HEADER} order.
     *
     * @param gameId game id
     * @param tick the tick to record
     * @return the row
     */
    static String row(String gameId, RunbyTick tick) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(tick.getFrame()));
        fields.add(Csv.name(tick.getSquadId()));
        fields.add(Csv.name(tick.getEvent()));
        fields.add(Csv.name(tick.getVerdict()));
        fields.add(Csv.name(tick.getPhase()));
        fields.add(Csv.name(tick.getGoalType()));
        fields.addAll(positionCells(tick.getSeekPoint()));
        fields.addAll(positionCells(tick.getAnchor()));
        fields.add(String.valueOf(tick.getAbortWindowOpen()));
        fields.add(Csv.format(tick.getEnemyTally()));
        fields.add(Csv.format(tick.getOurTally()));
        fields.add(String.valueOf(tick.getInBaseArea()));
        fields.add(String.valueOf(tick.getLings()));
        fields.add(String.valueOf(tick.getWorkersVisible()));
        fields.add(String.valueOf(tick.getExposedLings()));
        fields.add(String.valueOf(tick.getHpLost()));
        fields.add(String.valueOf(tick.getHpLostNonWorkerInReach()));
        fields.add(String.valueOf(tick.getWinnable()));
        fields.add(String.valueOf(tick.getBasesUnderAttack()));
        fields.add(String.valueOf(tick.getWorkersKilled()));
        fields.add(String.valueOf(tick.getBuildingsKilled()));
        return String.join(",", fields);
    }

    private static List<String> positionCells(Position position) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getX()));
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getY()));
        return fields;
    }
}
