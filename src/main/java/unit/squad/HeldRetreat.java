package unit.squad;

import java.util.Collection;

/**
 * The RETREAT a ground squad's siege band hold is keeping, with the frame it was set and the last frame the sim
 * evaluated the squad.
 *
 * <p>The memory lives on the squad rather than on its simulator, so a squad born of a merge or split inherits the
 * hold its sources held, see {@link #absorb}.
 */
public final class HeldRetreat {

    private int sinceFrame = -1;
    private int lastFrame = -1;

    /**
     * Whether a RETREAT is held and the sim last ran on this squad within {@code maxGap} frames of {@code frame}.
     *
     * @param frame the current frame
     * @param maxGap the longest gap between sim runs that still counts as continuous
     * @return true when the held RETREAT is still live
     */
    public boolean isLive(int frame, int maxGap) {
        return sinceFrame >= 0 && frame - lastFrame <= maxGap;
    }

    /**
     * Records a held RETREAT.
     *
     * @param since the frame the RETREAT was set
     * @param frame the frame the sim evaluated the squad
     */
    public void hold(int since, int frame) {
        sinceFrame = since;
        lastFrame = frame;
    }

    /**
     * Keeps the held RETREAT through a sim run that did not report it.
     *
     * @param frame the frame the sim evaluated the squad
     */
    public void keep(int frame) {
        lastFrame = frame;
    }

    /**
     * Drops the held RETREAT.
     *
     * @param frame the frame the sim evaluated the squad
     */
    public void clear(int frame) {
        sinceFrame = -1;
        lastFrame = frame;
    }

    /**
     * @return the frame the held RETREAT was set, -1 when none is held
     */
    public int getSinceFrame() {
        return sinceFrame;
    }

    /**
     * @return the last frame the sim evaluated the squad, -1 when it never has
     */
    public int getLastFrame() {
        return lastFrame;
    }

    /**
     * Folds the held RETREATs of merged or split sources into this memory. The latest RETREAT any source holds is
     * kept, matching the retreat lock, which folds to the latest expiry.
     *
     * @param sources squads this one is derived from
     */
    void absorb(Collection<Squad> sources) {
        for (Squad source : sources) {
            HeldRetreat other = source.getHeldRetreat();
            if (other.sinceFrame < 0) continue;
            sinceFrame = Math.max(sinceFrame, other.sinceFrame);
            lastFrame = Math.max(lastFrame, other.lastFrame);
        }
    }
}
