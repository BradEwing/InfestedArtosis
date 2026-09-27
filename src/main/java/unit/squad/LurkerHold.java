package unit.squad;

/**
 * Why a Lurker is sent out of fixed fire to a hold point, and why it lets go of one. The names are the reasons
 * telemetry_fixed_fire.csv records.
 */
final class LurkerHold {

    static final String HIT = "HIT";
    static final String RETREAT = "RETREAT";
    static final String MOVED = "MOVED";
    static final String COOLDOWN = "COOLDOWN";
    static final String TANK_ZONE = "TANK_ZONE";
    static final String RELEASE_COMMIT = "COMMIT";
    static final String RELEASE_CLEAR = "CLEAR";
    static final String RELEASE_STATUS = "STATUS";

    /**
     * Tuning value: frames a squad must have been committing without a break before its Lurkers commit with it,
     * letting go of their hold points and walking into sieged-tank reach. One second longer than a ground fight lock
     * ({@link Squad#fightHysteresis}, 3 s), so a single ENGAGE verdict and the lock it arms never commit the Lurkers:
     * the sim must say ENGAGE again, and keep the squad fighting, past the end of the first lock.
     */
    static final int COMMIT_FRAMES = 96;

    /**
     * Tuning value: pixels of margin out of the fire a new hold point must gain over the point a Lurker already holds
     * for the Lurker to be moved to it. One walk tile, so a point found again from where the Lurker stands never
     * unburrows it for no gain.
     */
    static final int MOVE_GAIN = 32;

    private LurkerHold() {
    }

    /**
     * Why a Lurker needs a new hold point this frame. One that already holds a point keeps it unless the fire has
     * moved onto it. One that holds none is sent out when it is hurt inside a sieged tank's reach, or when it is
     * retreating inside fixed fire that outranges it.
     *
     * @param holdCovered whether the point it holds is now inside fixed fire that outranges it
     * @param hitByTank whether it was hurt this frame inside a sieged tank's reach
     * @param retreatingInFire whether it holds the RETREAT role inside fixed fire that outranges it
     * @param holding whether it holds a point
     * @return {@link #MOVED}, {@link #HIT} or {@link #RETREAT}, or null when it needs no new point
     */
    static String reason(boolean holdCovered, boolean hitByTank, boolean retreatingInFire, boolean holding) {
        if (holding) {
            return holdCovered ? MOVED : null;
        }
        if (hitByTank) {
            return HIT;
        }
        return retreatingInFire ? RETREAT : null;
    }

    /**
     * Whether a Lurker holding a point the fire has moved onto is moved to a new point: only when the new point
     * stands at least {@link #MOVE_GAIN} further out of the fire.
     *
     * @param heldMargin margin of the point it holds out of the fire, negative inside it
     * @param newMargin margin of the new point out of the fire
     * @return true when the new point is worth unburrowing for
     */
    static boolean worthMoving(double heldMargin, double newMargin) {
        return newMargin >= heldMargin + MOVE_GAIN;
    }

    /**
     * The frame a squad's unbroken run of committing frames began.
     *
     * @param committing whether the squad commits this frame
     * @param since the frame its run began as of the last frame, or null when it was not committing
     * @param now current frame
     * @return the frame the run began, or null when the squad is not committing
     */
    static Integer committingSince(boolean committing, Integer since, int now) {
        if (!committing) {
            return null;
        }
        return since == null ? now : since;
    }

    /**
     * Whether a squad's Lurkers commit with it: it has been committing for at least {@link #COMMIT_FRAMES}.
     *
     * @param since the frame its run of committing frames began, or null when it is not committing
     * @param now current frame
     * @return true when its Lurkers commit
     */
    static boolean lurkersCommit(Integer since, int now) {
        return since != null && now - since >= COMMIT_FRAMES;
    }
}
