package info.tracking.terran;

import bwapi.UnitType;
import info.tracking.ObservedStrategy;
import info.tracking.ObservedUnitTracker;
import util.Time;

import java.util.Collections;
import java.util.List;

/**
 * <a href="https://liquipedia.net/starcraft/2_Rax_Academy_(vs._Zerg)">Liquipedia Entry</a>
 */
public class TwoRaxAcademy extends ObservedStrategy {

    public TwoRaxAcademy() { super("2RaxAcademy"); }

    @Override
    public boolean isDetected(ObservedUnitTracker tracker, Time time) {
        boolean detectedAcademy = tracker.getUnitTypeCountBeforeTime(UnitType.Terran_Academy, new Time(5, 0)) > 0;
        boolean detectedMedic = tracker.getUnitTypeCountBeforeTime(UnitType.Terran_Medic, new Time(5, 30)) > 0;
        boolean detectedFirebat = tracker.getUnitTypeCountBeforeTime(UnitType.Terran_Firebat, new Time(5, 30)) > 0;
        return detectedAcademy || detectedMedic || detectedFirebat;
    }

    @Override
    public List<ObservedStrategy> potentialTransitions() {
        return Collections.emptyList();
    }
}
