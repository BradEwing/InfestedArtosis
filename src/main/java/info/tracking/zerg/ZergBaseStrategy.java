package info.tracking.zerg;

import bwapi.Race;
import info.tracking.ObservedStrategy;

public abstract class ZergBaseStrategy extends ObservedStrategy {

    protected ZergBaseStrategy(String name) {
        super(name);
    }

    @Override
    public Race getRace() {
        return Race.Zerg;
    }
}
