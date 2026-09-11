package unit.managed;

import bwapi.Position;
import macro.plan.Plan;

import java.util.function.BiFunction;

/**
 * Tracks a builder's walk to its move target and the neutral blocker it diverts to. A builder is
 * stalled when it is more than {@link #ARRIVAL_DISTANCE} from its move target, is not harvesting,
 * and has not closed {@link #MIN_PROGRESS} on it within {@link #STALL_FRAMES}. Progress is measured
 * from an anchor that moves only when the builder closes {@link #MIN_PROGRESS}, so creeping forward
 * does not hide a stall. A plan change clears the blocker and the progress history.
 *
 * @param <B> the blocker type
 */
public class BuilderStall<B> {
    static final int ARRIVAL_DISTANCE = 150;
    static final int MIN_PROGRESS = 32;
    static final int STALL_FRAMES = 120;
    static final int BLOCKER_SEARCH_RADIUS = 96;

    private B blocker;
    private Position target;
    private double anchorDistance;
    private int anchorFrame;

    public B getBlocker() {
        return blocker;
    }

    public void divertTo(B blocker) {
        this.blocker = blocker;
        resetProgress();
    }

    public void clearBlocker() {
        blocker = null;
        resetProgress();
    }

    public void onPlanChange(Plan previous, Plan next) {
        if (previous == next) {
            return;
        }
        blocker = null;
        resetProgress();
    }

    /**
     * Diverts a stalled builder to the closest blocker within {@link #BLOCKER_SEARCH_RADIUS} of the
     * builder itself, never of its build site.
     *
     * @param builder the builder's position
     * @param target the builder's move target
     * @param distance the builder's distance to its move target, in pixels
     * @param harvesting whether the builder is gathering or carrying
     * @param frame the current frame
     * @param lookup finds the closest blocker within a pixel radius of a position
     * @return the blocker diverted to, or null when the builder is not stalled or no blocker is in range
     */
    public B divertIfStalled(Position builder, Position target, double distance, boolean harvesting, int frame,
            BiFunction<Position, Integer, B> lookup) {
        if (!isStalled(target, distance, harvesting, frame)) {
            return null;
        }
        B nearby = lookup.apply(builder, BLOCKER_SEARCH_RADIUS);
        if (nearby != null) {
            divertTo(nearby);
        }
        return nearby;
    }

    /**
     * @param target the builder's move target
     * @param distance the builder's distance to its move target, in pixels
     * @param harvesting whether the builder is gathering or carrying
     * @param frame the current frame
     * @return true when the builder is walking and has not closed {@link #MIN_PROGRESS} within {@link #STALL_FRAMES}
     */
    public boolean isStalled(Position target, double distance, boolean harvesting, int frame) {
        if (harvesting || distance <= ARRIVAL_DISTANCE) {
            resetProgress();
            return false;
        }
        if (!target.equals(this.target) || distance <= anchorDistance - MIN_PROGRESS) {
            this.target = target;
            anchorDistance = distance;
            anchorFrame = frame;
            return false;
        }
        return frame - anchorFrame >= STALL_FRAMES;
    }

    private void resetProgress() {
        target = null;
    }
}
