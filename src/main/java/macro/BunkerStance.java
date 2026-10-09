package macro;

import telemetry.BunkerStanceEvent;
import telemetry.BunkerTelemetry;

/**
 * Whether an enemy Bunker stance stands, so a {@link DroneRound.OpenReason#BUNKER_STANCE} round may open, and the
 * telemetry of each stance.
 *
 * <p>A stance stands while BunkerNatural or BunkerMain holds, no enemy Bunker has been broken, our army is not
 * attacking, the build is not an all-in by design and the economy answer is switched on, see {@link #evaluate}. It
 * starts on the first frame all of that holds and ends on the first frame one of it stops holding.
 *
 * <p>A Bunker hold is the span BunkerNatural or BunkerMain holds in, and it gets one Drone round. The stance id
 * numbers the holds: a stance that ends because our army attacks and starts again inside the same hold keeps the id,
 * and the Drone round does not open again for it. A hold that ends and a later one that starts are separate holds.
 *
 * <p>The decision is made in two steps each frame: {@link #setStatus} before the Drone round updates, so the round
 * can read {@link #isWanted}, and {@link #record} after it, which writes the BUNKER_ECON rows for the stance and
 * for the round it opened.
 */
public final class BunkerStance {

    /**
     * Whether a stance stands, and if not, which condition stops it. The first condition in this order that fails
     * is the one reported.
     */
    public enum Status {
        ACTIVE,
        SWITCH_OFF,
        BROKEN,
        NOT_HELD,
        ATTACKING,
        BUILD
    }

    static final String GAME_END = "GAME_END";

    private Status status = Status.NOT_HELD;
    private boolean heldBefore;
    private boolean active;
    private int stanceId;
    private int startedStanceId;
    private int extraPlanned;
    private int extraMade;
    private boolean roundOpen;
    private int roundDronesMade;
    private int lastDrones;
    private int lastWorkers;

    /**
     * Reads the conditions of a stance.
     *
     * @param econSwitch whether the economy answer is switched on
     * @param held whether BunkerNatural or BunkerMain holds
     * @param broken whether an enemy Bunker has been broken
     * @param attacking whether our army is attacking
     * @param buildAllows whether the current build and the strategy it plays take the economy answer
     * @return ACTIVE when a stance stands, otherwise the condition that stops it
     */
    public static Status evaluate(boolean econSwitch, boolean held, boolean broken, boolean attacking,
                                  boolean buildAllows) {
        if (!econSwitch) {
            return Status.SWITCH_OFF;
        }
        if (broken) {
            return Status.BROKEN;
        }
        if (!held) {
            return Status.NOT_HELD;
        }
        if (attacking) {
            return Status.ATTACKING;
        }
        if (!buildAllows) {
            return Status.BUILD;
        }
        return Status.ACTIVE;
    }

    /**
     * @return whether the conditions of a stance held at the last {@link #setStatus}
     */
    public boolean isWanted() {
        return status == Status.ACTIVE;
    }

    /**
     * Sets this frame's reading of the stance's conditions.
     *
     * @param status the reading, see {@link #evaluate}
     * @param held whether BunkerNatural or BunkerMain holds this frame, which starts a new hold when it did not the
     *     frame before
     */
    public void setStatus(Status status, boolean held) {
        if (held && !heldBefore) {
            stanceId++;
        }
        heldBefore = held;
        this.status = status;
    }

    /**
     * @return the number of the Bunker hold the stance stands in, or 0 when no stance stands
     */
    public int getStanceId() {
        return isWanted() ? stanceId : 0;
    }

    /**
     * Counts a Drone the open Bunker stance round queued at {@link macro.plan.UnitPlan#DRONE_ROUND_PRIORITY} as made.
     * A Drone made while no such round is open is not counted.
     */
    public void onRoundDroneMade() {
        if (roundOpen) {
            roundDronesMade++;
        }
    }

    /**
     * Writes the BUNKER_ECON rows this frame's reading and the Drone round call for: a stance start, a round opening
     * or closing, and a stance end carrying the Drones its rounds planned and made.
     *
     * @param frame the current frame
     * @param round the Drone round, already updated this frame
     * @param drones Drones hatched or in an egg
     * @param workers workers gathering
     */
    public void record(int frame, DroneRound round, int drones, int workers) {
        lastDrones = drones;
        lastWorkers = workers;
        if (isWanted() && !active) {
            active = true;
            if (startedStanceId != stanceId) {
                startedStanceId = stanceId;
                extraPlanned = 0;
                extraMade = 0;
            }
            BunkerTelemetry.stance(new BunkerStanceEvent(frame, "STANCE_START", Status.ACTIVE.name(), stanceId, drones,
                    workers, extraPlanned, extraMade));
        }
        boolean roundNow = round.isActive() && round.getReason() == DroneRound.OpenReason.BUNKER_STANCE;
        if (roundNow && !roundOpen) {
            roundOpen = true;
            roundDronesMade = 0;
            extraPlanned += round.getRoundSize();
            BunkerTelemetry.stance(new BunkerStanceEvent(frame, "ROUND_OPEN", DroneRound.OpenReason.BUNKER_STANCE.name(),
                    stanceId, drones, workers, extraPlanned, extraMade));
        } else if (!roundNow && roundOpen) {
            closeRound(frame, String.valueOf(round.getLastCloseReason()));
        }
        if (!isWanted() && active) {
            active = false;
            BunkerTelemetry.stance(new BunkerStanceEvent(frame, "STANCE_END", status.name(), stanceId, drones, workers,
                    extraPlanned, extraMade));
        }
    }

    /**
     * Writes the rows for the round and the stance still open when the game ends, with the reason GAME_END.
     *
     * @param frame the last frame
     */
    public void flush(int frame) {
        if (roundOpen) {
            closeRound(frame, GAME_END);
        }
        if (active) {
            active = false;
            BunkerTelemetry.stance(new BunkerStanceEvent(frame, "STANCE_END", GAME_END, stanceId, lastDrones,
                    lastWorkers, extraPlanned, extraMade));
        }
    }

    private void closeRound(int frame, String reason) {
        roundOpen = false;
        extraMade += roundDronesMade;
        BunkerTelemetry.stance(new BunkerStanceEvent(frame, "ROUND_CLOSE", reason, stanceId, lastDrones, lastWorkers,
                extraPlanned, extraMade));
    }
}
