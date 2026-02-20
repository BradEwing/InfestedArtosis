package info.tracking;

import info.BaseData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.Time;

@Getter
@RequiredArgsConstructor
public class StrategyDetectionContext {
    private final ObservedUnitTracker tracker;
    private final Time time;
    private final BaseData baseData;
}
