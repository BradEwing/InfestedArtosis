package info.tracking.protoss;

import bwapi.UnitType;
import info.tracking.ObservedStrategy;
import info.tracking.ObservedUnitTracker;
import util.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * https://liquipedia.net/starcraft/2_Gateway_(vs._Zerg)
 */
public class TwoGate extends ProtossBaseStrategy {

    public TwoGate() {
        super("2Gate");
    }

    @Override
    public boolean isDetected(ObservedUnitTracker tracker, Time time) {
        if (tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Zealot, new Time(3, 10)) >= 3 ||
                tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Zealot, new Time(3, 35)) >= 4 ||
                tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Zealot, new Time(4, 0)) >= 5 ) {
            return true;
        }
        if (tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Gateway, new Time(3, 0)) >= 2) {
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
        if (Objects.equals(name, "FFE") || Objects.equals(name, "1GateCore")) {
            return false;
        }
        return true;
    }
}
