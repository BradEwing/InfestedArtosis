package info.tracking.terran;

import bwapi.Race;
import info.tracking.ObservedStrategy;

public abstract class TerranBaseStrategy extends ObservedStrategy {

    protected TerranBaseStrategy(String name) {
        super(name);
    }

    @Override
    public Race getRace() { 
        return Race.Terran; 
    }
}