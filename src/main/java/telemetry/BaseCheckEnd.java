package telemetry;

import unit.scout.BaseCheckScheduler;

/**
 * How a base check ended: when, why, whether an enemy stood near the base, and when the scout died.
 */
public final class BaseCheckEnd {

    public static final int SURVIVED = -1;

    private final int endFrame;
    private final BaseCheckScheduler.Release outcome;
    private final boolean occupied;
    private final int diedFrame;
    private final boolean primary;

    /**
     * @param endFrame the frame the check ended, which for a check that saw its base is the arrival frame
     * @param outcome why the check ended
     * @param occupied whether an enemy stood within sight of the base when the check ended
     * @param diedFrame the frame the scout died, or {@link #SURVIVED}
     */
    public BaseCheckEnd(int endFrame, BaseCheckScheduler.Release outcome, boolean occupied, int diedFrame) {
        this(endFrame, outcome, occupied, diedFrame, true);
    }

    /**
     * @param primary false for the second zergling of a pair sent to one base
     */
    public BaseCheckEnd(int endFrame, BaseCheckScheduler.Release outcome, boolean occupied, int diedFrame,
                        boolean primary) {
        this.endFrame = endFrame;
        this.outcome = outcome;
        this.occupied = occupied;
        this.diedFrame = diedFrame;
        this.primary = primary;
    }

    public int getEndFrame() {
        return endFrame;
    }

    public BaseCheckScheduler.Release getOutcome() {
        return outcome;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public int getDiedFrame() {
        return diedFrame;
    }

    public boolean isPrimary() {
        return primary;
    }
}
