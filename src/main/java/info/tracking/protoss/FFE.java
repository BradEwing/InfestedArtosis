package info.tracking.protoss;

import info.tracking.ObservedStrategy;
import info.tracking.ObservedUnitTracker;
import util.Time;

import java.util.Objects;

/**
 * https://liquipedia.net/starcraft/Forge_FE_(vs._Zerg)
 */
public class FFE extends ProtossBaseStrategy {

    public FFE() {
        super("FFE");
    }

    @Override
    public boolean isDetected(ObservedUnitTracker tracker, Time time) {
        // TODO: implement
        return false;
    }

    @Override
    public boolean isCompatibleStrategy(ObservedStrategy strategy) {
        String name = strategy.getName();
        if (Objects.equals(name, "2Gate") || Objects.equals(name, "1GateCore")) {
            return false;
        }
        return true;
    }
}
