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
     * Returns true if the strategy is detected based on the provided detection context.
     */
    public abstract boolean isDetected(StrategyDetectionContext context);

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
