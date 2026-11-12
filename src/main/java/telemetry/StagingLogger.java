package telemetry;

import bwapi.Game;

/**
 * Writes telemetry_staging.csv: the standoff episodes of ground squads, the reinforcements sent to an army's rear, and
 * the launches of ground squads out of the rally point.
 *
 * <p>A STANDOFF_ENTER row is written when a squad starts gathering at a standoff point, and a STANDOFF_EXIT row when
 * it stops, with the reason in reason: GATHERED when enough of its supply was within the gather radius of the standoff
 * point, TIMEOUT when the timeout passed, ENEMY_ARRIVED when the enemy came inside the standoff distance, ABANDONED
 * when the squad stopped being staged for another reason, RETREAT when the verdict or the shared verdict turned it to
 * retreat, ENGAGED when the shared verdict or a neighbour's fight lock sent it at the enemy, CONTAIN when it left to
 * join a contain, DIED when its last member died, and MERGED when the squad merged into another. A merged squad
 * that carries the episode on writes another STANDOFF_ENTER under its own id with reason MERGED and the same
 * episode_id; the first STANDOFF_ENTER of an episode_id is its launch anchor. A squad clumped at its standoff point on
 * its first reading writes STANDOFF_ENTER and a GATHERED STANDOFF_EXIT on the same frame.
 *
 * <p>episode_id numbers the standoff episodes of a game from 0 and is on every STANDOFF_ENTER and STANDOFF_EXIT row.
 * member_unit_ids lists, joined by semicolons, the unit ids of the squad's members on STANDOFF_ENTER, REINFORCE_REAR
 * and LAUNCH rows, NONE on any other. The same episode_id is written per unit in telemetry_engagement_units.csv.
 *
 * <p>A REINFORCE_REAR row is written once per squad when a squad that would have launched is sent to the rear of a
 * larger friendly army, with that army's squad id in target_squad_id and its last standoff episode in episode_id.
 * With the switch on, a REINFORCE_OUTCOME row closes each routed squad, reason JOINED when it merged with the army it
 * was sent to, DIED when its last member died, MERGED_ELSEWHERE when it merged with another squad (the merged squad
 * carries the routing on under its own squad_id when no squad in the merge reached its army), TARGET_GONE when the
 * army no longer exists, TARGET_IDLE when the army stopped fighting or retreating, ARRIVED when enemies came near the
 * squad inside the army's support radius, THREATENED when they came near it farther out or a base was threatened, and
 * PREEMPTED when the squad left the rally status or took another path.
 *
 * <p>A LAUNCH row is written when a ground squad leaves the rally point on its own, not routed to an army, and a
 * LAUNCH_HELD row once per hold when a ground squad cleared to launch is kept from launching alone because its supply
 * is below min_mass, the minimum supply a ground squad launches alone with at that game time, and it has no army's
 * rear to join: reason HOME when it stays at the rally point, CONTAIN when it is sent to a contain's arc instead.
 * A LAUNCH row's reason is NONE at or above min_mass, BELOW_MIN_MASS below it with the switch off, and EXEMPT below
 * it for a squad the rule does not hold: an all-in or rush build, a threatened base, or a move out threshold lowered
 * below the base ground threshold for a cannon rush. squad_supply and min_mass are in BWAPI supply units, twice the
 * displayed supply. Count lone small launches as LAUNCH rows with squad_supply below min_mass.
 *
 * <p>applied is false when the staging switch is off: the squad is read and an episode is opened and closed, but it
 * acts as it does without staging, so the off arm yields comparable STANDOFF_ENTER counts only; its frames_staged
 * and exit reasons describe a squad that kept walking in. gathered_share is the share of the squad's
 * supply within the gather radius of its standoff point, enemy_distance the pixels from the nearest member to the
 * nearest measured enemy, standoff_distance the distance the squad gathers at, and frames_staged the frames the
 * episode has lasted, nearest_enemy_type the type of the measured enemy nearest the squad centre and enemy_reach the
 * longest ground reach among the measured enemies, with extents. Rows with no such reading carry -1 or NONE.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class StagingLogger implements StagingSink {

    static final String FILE = "telemetry_staging.csv";

    static final String HEADER = "game_id,frame,event,squad_id,reason,applied,squad_supply,members,gathered_share,"
            + "enemy_distance,standoff_distance,frames_staged,nearest_enemy_type,enemy_reach,episode_id,"
            + "member_unit_ids,target_squad_id,min_mass";

    static final String EVENT_ENTER = "STANDOFF_ENTER";
    static final String EVENT_EXIT = "STANDOFF_EXIT";
    static final String EVENT_REINFORCE = "REINFORCE_REAR";
    static final String EVENT_REINFORCE_OUTCOME = "REINFORCE_OUTCOME";
    static final String EVENT_LAUNCH = "LAUNCH";
    static final String EVENT_LAUNCH_HELD = "LAUNCH_HELD";

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
    public void onStandoffEnter(int frame, String reason, StagingRow row) {
        append(row(gameId, frame, EVENT_ENTER, reason, row));
    }

    @Override
    public void onStandoffExit(int frame, String reason, StagingRow row) {
        append(row(gameId, frame, EVENT_EXIT, reason, row));
    }

    @Override
    public void onReinforcement(int frame, StagingRow row) {
        append(row(gameId, frame, EVENT_REINFORCE, NONE, row));
    }

    @Override
    public void onReinforcementOutcome(int frame, String outcome, StagingRow row) {
        append(row(gameId, frame, EVENT_REINFORCE_OUTCOME, outcome, row));
    }

    @Override
    public void onLaunch(int frame, String reason, StagingRow row) {
        append(row(gameId, frame, EVENT_LAUNCH, reason, row));
    }

    @Override
    public void onLaunchHeld(int frame, String reason, StagingRow row) {
        append(row(gameId, frame, EVENT_LAUNCH_HELD, reason, row));
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
                String.valueOf(row.getStandoffDistance()), String.valueOf(row.getFramesStaged()), row.getEnemyType(),
                String.valueOf(row.getEnemyReach()), String.valueOf(row.getEpisodeId()), row.getMemberUnitIds(),
                row.getTargetSquadId(), String.valueOf(row.getMinMass()));
    }
}
