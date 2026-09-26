package macro.plan;

import bwapi.Position;
import unit.managed.BuilderStall;

/**
 * Tracks whether a dispatched builder has strayed from a site its plan can already pay for.
 *
 * <p>Nothing is measured until the grace period after dispatch has passed: the walk the dispatch
 * was timed for, {@link util.TravelTime}, times {@link #GRACE_MARGIN_PERCENT}. After it, only a
 * builder whose plan is affordable and that stands farther than {@link #STRAY_DISTANCE} from its
 * move target is measured.
 *
 * <p>Progress is measured from an anchor. A builder heading to the site, one whose order is a move
 * to the plan's move target, moves the anchor whenever its position moves {@link #CLOSING_PROGRESS}
 * from it, so a builder walking a ground route that bends away from the site in a straight line is
 * never strayed. Any builder also moves the anchor when it closes {@link #CLOSING_PROGRESS} on the
 * target. A builder that has not moved the anchor is strayed once either holds:
 * <ul>
 *     <li>it is not heading to the site, {@link #STRAY_FRAMES} have passed and it stands more than
 *     {@link #CLOSING_PROGRESS} farther than its anchor, so it is walking away on another order;
 *     or</li>
 *     <li>{@link #STUCK_FRAMES} have passed, so it is standing still. That window is twice
 *     {@link BuilderStall#STALL_FRAMES}, so a builder held up by a blocking mineral is diverted to
 *     it first.</li>
 * </ul>
 * The grace period, an unaffordable plan, a builder inside the stray distance, and a builder excused
 * from the walk each clear the history.
 */
public class BuilderStray {
    static final int STRAY_DISTANCE = 2 * BuilderStall.ARRIVAL_DISTANCE;
    static final int CLOSING_PROGRESS = 32;
    static final int STRAY_FRAMES = 48;
    static final int STUCK_FRAMES = 2 * BuilderStall.STALL_FRAMES;
    static final int GRACE_MARGIN_PERCENT = 150;

    private static final int NO_ANCHOR = -1;

    private final int graceEndFrame;
    private double anchorDistance;
    private Position anchorPosition;
    private int anchorFrame = NO_ANCHOR;

    /**
     * @param dispatchFrame the frame the builder was dispatched
     * @param travelFrames the frames the dispatch expected the walk to the site to take
     */
    public BuilderStray(int dispatchFrame, int travelFrames) {
        this.graceEndFrame = dispatchFrame + travelFrames * GRACE_MARGIN_PERCENT / 100;
    }

    /**
     * @param position the builder's position
     * @param distance the builder's distance to its move target, in pixels
     * @param headingToSite whether the builder's order is a move to the plan's move target
     * @param affordable whether the bank covers the plan's own cost
     * @param excused whether the builder is away from the walk on purpose: returning cargo or
     *     clearing a blocking mineral
     * @param frame the current frame
     * @return true when the builder has strayed from an affordable site
     */
    public boolean isStrayed(Position position, double distance, boolean headingToSite, boolean affordable,
            boolean excused, int frame) {
        if (frame < graceEndFrame || !affordable || excused || distance <= STRAY_DISTANCE) {
            anchorFrame = NO_ANCHOR;
            return false;
        }
        if (anchorFrame == NO_ANCHOR || distance <= anchorDistance - CLOSING_PROGRESS
                || headingToSite && position.getDistance(anchorPosition) >= CLOSING_PROGRESS) {
            anchorDistance = distance;
            anchorPosition = position;
            anchorFrame = frame;
            return false;
        }
        int sinceProgress = frame - anchorFrame;
        boolean receding = !headingToSite && distance > anchorDistance + CLOSING_PROGRESS;
        return sinceProgress >= STUCK_FRAMES || receding && sinceProgress >= STRAY_FRAMES;
    }
}
