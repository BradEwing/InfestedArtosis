package info.tracking;

import info.BaseData;
import info.map.GameMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.Time;

@Getter
@RequiredArgsConstructor
public class StrategyDetectionContext {
    private final ObservedUnitTracker tracker;
    private final Time time;
    private final BaseData baseData;
    private final GameMap gameMap;
}
