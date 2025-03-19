package info.tracking;

import bwapi.TilePosition;
import bwapi.Unit;
import lombok.Data;
import util.Time;

@Data
public class ObservedUnit {
    private Time firstObservedFrame;
    private Time lastObservedFrame;
    private Time destroyedFrame;
    private TilePosition lastKnownLocation;
    private final Unit unit;

    public ObservedUnit(Unit unit, Time currentFrame) {
        this.unit = unit;
        this.firstObservedFrame = currentFrame;
        this.lastObservedFrame = currentFrame;
        this.lastKnownLocation = unit.getTilePosition();
    }

    @Override
    public int hashCode() {
        return unit.hashCode();
    }
}
