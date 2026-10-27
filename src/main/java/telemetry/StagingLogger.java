package telemetry;

import bwapi.Game;

/**
 * Writes telemetry_staging.csv: the standoff episodes of ground squads and the reinforcements sent to an army's rear.
 *
 * <p>A STANDOFF_ENTER row is written when a squad starts gathering at a standoff point, and a STANDOFF_EXIT row when
 * it stops, with the reason in reason: GATHERED when enough of its supply was within the gather radius, TIMEOUT when
 * the timeout passed, ENEMY_ARRIVED when the enemy came inside the standoff distance, and ABANDONED when the squad
 * stopped being staged for another reason. A REINFORCE_REAR row is written when a squad that would have launched is
 * sent to the rear of a larger friendly army, and then once per squad.
 *
 * <p>applied is false when the staging switch is off: the episode is read and logged as it would have run, and the
 * squad acts as it does without staging, so the off arm stays measurable. gathered_share is the share of the squad's
 * supply within the gather radius of its centre, enemy_distance the pixels from the nearest member to the nearest
 * measured enemy, standoff_distance the distance the squad gathers at, and frames_staged the frames the episode has
 * lasted. Rows with no episode carry -1 in those columns.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class StagingLogger implements StagingSink {

    static final String FILE = "telemetry_staging.csv";

    static final String HEADER = "game_id,frame,event,squad_id,reason,applied,squad_supply,members,gathered_share,"
            + "enemy_distance,standoff_distance,frames_staged";

    static final String EVENT_ENTER = "STANDOFF_ENTER";
    static final String EVENT_EXIT = "STANDOFF_EXIT";
    static final String EVENT_REINFORCE = "REINFORCE_REAR";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final String NONE = "NONE";

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public StagingLogger(Game game, String gameId) {
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
    public void onStandoffEnter(int frame, StagingRow row) {
        append(row(gameId, frame, EVENT_ENTER, NONE, row));
    }

    @Override
    public void onStandoffExit(int frame, String reason, StagingRow row) {
        append(row(gameId, frame, EVENT_EXIT, reason, row));
    }

    @Override
    public void onReinforcement(int frame, StagingRow row) {
        append(row(gameId, frame, EVENT_REINFORCE, NONE, row));
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
        StagingTelemetry.clear();
    }

    /**
     * Builds a row in {@link #HEADER} order.
     */
    static String row(String gameId, int frame, String event, String reason, StagingRow row) {
        return String.join(",", gameId, String.valueOf(frame), event, row.getSquadId(), reason,
                String.valueOf(row.isApplied()), String.valueOf(row.getSupply()), String.valueOf(row.getMembers()),
                Csv.format(row.getGatheredShare()), String.valueOf(row.getEnemyDistance()),
                String.valueOf(row.getStandoffDistance()), String.valueOf(row.getFramesStaged()));
    }
}
