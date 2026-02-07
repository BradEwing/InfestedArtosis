package info.tracking.protoss;

import bwapi.UnitType;
import info.tracking.ObservedStrategy;
import info.tracking.ObservedUnitTracker;
import util.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Not a standard ZvP opening in bot land, but could augur a DT rush.
 */
public class OneGateCore extends ProtossBaseStrategy {
    public OneGateCore() {
        super("1GateCore");
    }

    @Override
    public boolean isDetected(ObservedUnitTracker tracker, Time time) {
        // TODO: Check against gas steal
        if (tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Assimilator, new Time(2, 45)) >= 1) {
            return true;
        }
        if (tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Cybernetics_Core, new Time(3, 30)) >= 1) {
            return true;
        }
        // Estimate by goon count
        if (tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Dragoon, new Time(4, 10)) >= 1 ||
            tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Dragoon, new Time(4, 30)) >= 2) {
            return true;
        }
        return false;
    }

    @Override
    public List<ObservedStrategy> potentialTransitions() {
        List<ObservedStrategy> potential = new ArrayList<>();
        return potential;
    }

    @Override
    public boolean isCompatibleStrategy(ObservedStrategy strategy) {
        String name = strategy.getName();
        if (Objects.equals(name, "FFE") || Objects.equals(name, "2Gate")) {
            return false;
        }
        return true;
    }
}
