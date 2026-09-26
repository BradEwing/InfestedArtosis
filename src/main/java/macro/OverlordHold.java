package macro;

/**
 * Follows the active build order's Overlord hold from frame to frame, so the supply planner can
 * tell the frame the hold releases from the frames after it. The hold is read from whichever
 * build order is active, so a release that comes with an opener handing over is still seen.
 */
class OverlordHold {

    /** Where the hold stands on the frame it was last read. */
    enum Phase {
        /** The build order holds every Overlord. */
        HELD,
        /** The hold was on when last read and is off now. */
        RELEASED,
        /** The hold is off and was off when last read. */
        FREE
    }

    private boolean held = false;

    /**
     * Records this frame's hold.
     *
     * @param holdsOverlords whether the active build order holds Overlords this frame
     * @return the hold's phase on this frame
     */
    Phase update(boolean holdsOverlords) {
        final boolean wasHeld = held;
        held = holdsOverlords;
        if (holdsOverlords) {
            return Phase.HELD;
        }
        return wasHeld ? Phase.RELEASED : Phase.FREE;
    }
}
