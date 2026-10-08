package macro;

import telemetry.BunkerStanceEvent;
import telemetry.BunkerTelemetry;

/**
 * Whether an enemy Bunker stance stands, so a {@link DroneRound.OpenReason#BUNKER_STANCE} round may open, and the
 * telemetry of each stance.
 *
 * <p>A stance stands while BunkerNatural or BunkerMain holds, no enemy Bunker has been broken, our army is not
 * attacking, the build is not an all-in by design and the economy answer is switched on, see {@link #evaluate}. It
 * starts on the first frame all of that holds and ends on the first frame one of it stops holding. A stance that
 * ends and later starts again is a new stance.
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

    private Status status = Status.NOT_HELD;
    private boolean active;
    private int stanceId;
    private int extraPlanned;
    private int extraMade;
    private boolean roundOpen;
    private int dronesAtRoundOpen;

    /**
     * Reads the conditions of a stance.
     *
     * @param econSwitch whether the economy answer is switched on
     * @param held whether BunkerNatural or BunkerMain holds
     * @param broken whether an enemy Bunker has been broken
     * @param attacking whether our army is attacking
     * @param buildAllows whether the current build takes the economy answer
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
     */
    public void setStatus(Status status) {
        if (status == Status.ACTIVE && !isWanted()) {
            stanceId++;
        }
        this.status = status;
    }

    /**
     * @return the number of the stance that stands, or 0 when none does
     */
    public int getStanceId() {
        return isWanted() ? stanceId : 0;
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
        if (isWanted() && !active) {
            active = true;
            extraPlanned = 0;
            extraMade = 0;
            BunkerTelemetry.stance(new BunkerStanceEvent(frame, "STANCE_START", Status.ACTIVE.name(), stanceId, drones,
                    workers, 0, 0));
        }
        boolean roundNow = round.isActive() && round.getReason() == DroneRound.OpenReason.BUNKER_STANCE;
        if (roundNow && !roundOpen) {
            roundOpen = true;
            dronesAtRoundOpen = round.getDrones();
            extraPlanned += round.getRoundSize();
            BunkerTelemetry.stance(new BunkerStanceEvent(frame, "ROUND_OPEN", DroneRound.OpenReason.BUNKER_STANCE.name(),
                    stanceId, drones, workers, extraPlanned, extraMade));
        } else if (!roundNow && roundOpen) {
            roundOpen = false;
            extraMade += Math.max(0, round.getDrones() - dronesAtRoundOpen);
            BunkerTelemetry.stance(new BunkerStanceEvent(frame, "ROUND_CLOSE",
                    String.valueOf(round.getLastCloseReason()), stanceId, drones, workers, extraPlanned, extraMade));
        }
        if (!isWanted() && active) {
            active = false;
            BunkerTelemetry.stance(new BunkerStanceEvent(frame, "STANCE_END", status.name(), stanceId, drones, workers,
                    extraPlanned, extraMade));
        }
    }
}
