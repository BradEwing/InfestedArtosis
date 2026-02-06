package info.tracking;

import bwapi.Race;
import util.Time;

import java.util.List;

public abstract class ObservedStrategy {
    private static final int LOCK_AFTER_MINUTES = 59;
    private static final int LOCK_AFTER_SECONDS = 59;

    private final String name;

    protected ObservedStrategy(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns true if the strategy is detected based on the ObservedUnitTracker
     * data and the current frame.
     */
    public abstract boolean isDetected(ObservedUnitTracker tracker, Time time);

    /**
     * Returns a list of all possible transitions from this strategy to other
     * strategies. The returned list should be sorted in order of decreasing
     * likelihood.
     */
    public abstract List<ObservedStrategy> potentialTransitions();

    public boolean isCompatibleStrategy(ObservedStrategy other) {
        return false;
    }

    public Time lockAfter() {
        return new Time(LOCK_AFTER_MINUTES, LOCK_AFTER_SECONDS);
    }

    public Race getRace() {
        return Race.Unknown;
    }
}
