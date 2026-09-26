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
    static final String RELEASE_COMMIT = "COMMIT";
    static final String RELEASE_CLEAR = "CLEAR";
    static final String RELEASE_STATUS = "STATUS";

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
}
