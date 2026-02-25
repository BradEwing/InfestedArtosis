package info.tracking.any;

import info.BaseData;
import info.tracking.ObservedStrategy;
import info.tracking.StrategyDetectionContext;
import util.Time;

public class OneBase extends ObservedStrategy {

    private static final Time FOUR_MINUTES = new Time(4, 0);

    public OneBase() {
        super("1Base");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        Time time = context.getTime();
        BaseData baseData = context.getBaseData();
        if (time.lessThanOrEqual(FOUR_MINUTES)) {
            return false;
        }
        return baseData.getEnemyBases().size() == 1;
    }

}
