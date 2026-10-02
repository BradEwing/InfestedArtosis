package unit.squad;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Reads an air squad that keeps crossing between FIGHT and RETREAT against the same enemy without killing it.
 *
 * <p>Every flip between FIGHT and RETREAT is a crossing. The squad is stalled when at least {@link #CROSSINGS}
 * crossings fall within the last {@link #WINDOW_FRAMES} frames and no enemy died near the squad since the oldest of
 * them. A stalled squad is offered a harass on another target before it engages the same enemy again. The constants
 * are tuning values, not Brood War facts.
 */
public final class AirStallDetector {

    /** Tuning value: FIGHT and RETREAT crossings within the window that make a stall. */
    static final int CROSSINGS = 4;
    /** Tuning value: frames the crossings must fall within. */
    static final int WINDOW_FRAMES = 720;

    private final Deque<Integer> crossings = new ArrayDeque<>();
    private SquadStatus lastSide;
    private int lastKillFrame = -1;

    /**
     * Records the squad's status for this frame. A flip between FIGHT and RETREAT is a crossing; any other status
     * ends the run, since the squad is no longer flapping.
     *
     * @param now current frame
     * @param status the squad's status
     */
    public void observe(int now, SquadStatus status) {
        if (status != SquadStatus.FIGHT && status != SquadStatus.RETREAT) {
            reset();
            return;
        }
        if (lastSide != null && lastSide != status) {
            crossings.addLast(now);
        }
        lastSide = status;
        prune(now);
    }

    /**
     * Records an enemy death near the squad, which ends a no-kill run.
     *
     * @param now current frame
     */
    public void onKill(int now) {
        lastKillFrame = now;
    }

    /**
     * @param now current frame
     * @return true when {@link #CROSSINGS} crossings fall within {@link #WINDOW_FRAMES} and the squad killed nothing
     * since the oldest of them
     */
    public boolean isStalled(int now) {
        prune(now);
        return crossings.size() >= CROSSINGS && lastKillFrame < crossings.peekFirst();
    }

    /**
     * Forgets the crossings, for a squad that left the flap by entering a harass.
     */
    public void reset() {
        crossings.clear();
        lastSide = null;
    }

    private void prune(int now) {
        while (!crossings.isEmpty() && now - crossings.peekFirst() > WINDOW_FRAMES) {
            crossings.removeFirst();
        }
    }
}
