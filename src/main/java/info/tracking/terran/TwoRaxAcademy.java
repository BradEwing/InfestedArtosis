package info.tracking.terran;

import bwapi.UnitType;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

/**
 * <a href="https://liquipedia.net/starcraft/2_Rax_Academy_(vs._Zerg)">Liquipedia Entry</a>
 */
public class TwoRaxAcademy extends TerranBaseStrategy {

    public TwoRaxAcademy() { 
        super("2RaxAcademy"); 
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        ObservedUnitTracker tracker = context.getTracker();
        boolean detectedAcademy = tracker.getUnitTypeCountBeforeTime(UnitType.Terran_Academy, new Time(5, 0)) > 0;
        boolean detectedMedic = tracker.getUnitTypeCountBeforeTime(UnitType.Terran_Medic, new Time(5, 30)) > 0;
        boolean detectedFirebat = tracker.getUnitTypeCountBeforeTime(UnitType.Terran_Firebat, new Time(5, 30)) > 0;
        return detectedAcademy || detectedMedic || detectedFirebat;
    }
}
