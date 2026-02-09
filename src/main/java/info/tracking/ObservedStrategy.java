package info.tracking;

import bwapi.Race;
import util.Time;

public abstract class ObservedStrategy {
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

    public boolean isCompatibleStrategy(ObservedStrategy other) {
        return false;
    }

    public Time lockAfter() {
        return new Time(59, 59);
    }

    public Race getRace() { 
        return Race.Unknown;
    }
}
