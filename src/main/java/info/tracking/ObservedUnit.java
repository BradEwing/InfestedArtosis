package info.tracking;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import lombok.Data;
import util.Time;

@Data
public class ObservedUnit {
    private Time firstObservedFrame;
    private Time lastObservedFrame;
    private Time destroyedFrame;
    private Position lastKnownLocation;
    private final Unit unit;
    private UnitType unitType;
    private boolean proxied;

    public ObservedUnit(Unit unit, Time currentFrame, boolean proxied) {
        this.unit = unit;
        this.unitType = unit.getType();
        this.firstObservedFrame = currentFrame;
        this.lastObservedFrame = currentFrame;
        this.lastKnownLocation = unit.getPosition();
        this.proxied = proxied;
    }

    @Override
    public int hashCode() {
        return unit.hashCode();
    }
}
