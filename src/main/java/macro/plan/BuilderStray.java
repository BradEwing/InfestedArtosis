package macro.plan;

import unit.managed.BuilderStall;

/**
 * Tracks whether a dispatched builder has strayed from a site its plan can already pay for.
 *
 * <p>Only a builder whose plan is affordable and that stands farther than {@link #STRAY_DISTANCE}
 * from its move target is measured. Progress is measured from an anchor that moves only when the
 * builder closes {@link #CLOSING_PROGRESS} on the target, so a builder walking toward the site is
 * never strayed. A builder that has not closed on the target is strayed once either holds:
 * <ul>
 *     <li>{@link #STRAY_FRAMES} have passed and it stands more than {@link #CLOSING_PROGRESS}
 *     farther than its anchor, so it is walking away; or</li>
 *     <li>{@link #STUCK_FRAMES} have passed, so it is standing still. That window is twice
 *     {@link BuilderStall#STALL_FRAMES}, so a builder held up by a blocking mineral is diverted to
 *     it first.</li>
 * </ul>
 * An unaffordable plan, a builder inside the stray distance, and a builder excused from the walk
 * each clear the history.
 */
public class BuilderStray {
    static final int STRAY_DISTANCE = 2 * BuilderStall.ARRIVAL_DISTANCE;
    static final int CLOSING_PROGRESS = 32;
    static final int STRAY_FRAMES = 48;
    static final int STUCK_FRAMES = 2 * BuilderStall.STALL_FRAMES;

    private static final int NO_ANCHOR = -1;

    private double anchorDistance;
    private int anchorFrame = NO_ANCHOR;

    /**
     * @param distance the builder's distance to its move target, in pixels
     * @param affordable whether the bank covers the plan's own cost
     * @param excused whether the builder is away from the walk on purpose: returning cargo or
     *     clearing a blocking mineral
     * @param frame the current frame
     * @return true when the builder has strayed from an affordable site
     */
    public boolean isStrayed(double distance, boolean affordable, boolean excused, int frame) {
        if (!affordable || excused || distance <= STRAY_DISTANCE) {
            anchorFrame = NO_ANCHOR;
            return false;
        }
        if (anchorFrame == NO_ANCHOR || distance <= anchorDistance - CLOSING_PROGRESS) {
            anchorDistance = distance;
            anchorFrame = frame;
            return false;
        }
        int sinceClosing = frame - anchorFrame;
        boolean receding = distance > anchorDistance + CLOSING_PROGRESS;
        return sinceClosing >= STUCK_FRAMES || receding && sinceClosing >= STRAY_FRAMES;
    }
}
