package info.tracking;

import bwapi.TilePosition;
import bwapi.Unit;
import lombok.Data;

@Data
public class ObservedUnit {
    private final int firstObservedFrame;
    private int lastObservedFrame;
    private int destroyedFrame;
    private TilePosition lastKnownLocation;
    private final Unit unit;

    public ObservedUnit(Unit unit, int currentFrame) {
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
