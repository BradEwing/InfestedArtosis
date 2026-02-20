package info.tracking;

import info.BaseData;
import lombok.Getter;
import util.Time;

@Getter
public class StrategyDetectionContext {
    private final ObservedUnitTracker tracker;
    private final Time time;
    private final BaseData baseData;

    public StrategyDetectionContext(ObservedUnitTracker tracker, Time time, BaseData baseData) {
        this.tracker = tracker;
        this.time = time;
        this.baseData = baseData;
    }
}
