package info.tracking.protoss;

import bwapi.Race;
import info.tracking.ObservedStrategy;

public abstract class ProtossBaseStrategy extends ObservedStrategy {

    protected ProtossBaseStrategy(String name) {
        super(name);
    }

    @Override
    public Race getRace() {return Race.Protoss;}
}
