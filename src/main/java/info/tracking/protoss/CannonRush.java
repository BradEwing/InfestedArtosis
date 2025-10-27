package info.tracking.protoss;

import bwapi.UnitType;
import info.tracking.ObservedStrategy;
import info.tracking.ObservedUnitTracker;
import util.Time;

import java.util.Collections;
import java.util.List;

/**
 * Detects cannon rush strategy by identifying pylons or photon cannons
 * within 10 tiles (manhattan distance) of our bases.
 * <p>
 * To defeat the Protoss, we must understand the Protoss
 * <a href="https://github.com/dgant/PurpleWave/blob/ea41b10c5d5f44cff0beb9f8dae123ad573fddb8/src/Information/Fingerprinting/ProtossStrategies/FingerprintCannonRush.scala#L4">Thanks PurpleWave</a>
 */
public class CannonRush extends ProtossBaseStrategy {


    public CannonRush() {
        super("CannonRush");
    }

    @Override
    public boolean isDetected(ObservedUnitTracker tracker, Time time) {
        Time detectedBy = new Time(4, 30);
        int pylons = tracker.getProxiedCountByTypeBeforeTime(UnitType.Protoss_Pylon, detectedBy);
        int cannons = tracker.getProxiedCountByTypeBeforeTime(UnitType.Protoss_Photon_Cannon, detectedBy);
        return pylons + cannons > 0;
    }

    @Override
    public List<ObservedStrategy> potentialTransitions() {
        return Collections.emptyList();
    }
}