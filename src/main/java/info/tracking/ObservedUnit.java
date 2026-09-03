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
    private Time completedFrame;
    private Position lastKnownLocation;
    private final Unit unit;
    private UnitType unitType;
    private boolean proxied;
    private boolean completed;
    private int lastKnownHitPoints;
    private int lastKnownShields;
    private int lastKnownGroundHeight = -1;
    private int lastKnownLoadedCount = -1;
    private int lastLoadedCheckFrame = -1;
    private int lastBunkerBulletFrame = -1;

    public ObservedUnit(Unit unit, Time currentFrame, boolean proxied) {
        this.unit = unit;
        this.unitType = unit.getType();
        this.firstObservedFrame = currentFrame;
        this.lastObservedFrame = currentFrame;
        this.lastKnownLocation = unit.getPosition();
        this.proxied = proxied;
        this.lastKnownHitPoints = unit.getType().maxHitPoints();
        this.lastKnownShields = unit.getType().maxShields();
    }

    /**
     * @return the unit's live position while it is visible, otherwise the last position it was seen at,
     *     or null when the last known position has been ruled out by observation
     */
    public Position getCurrentOrLastKnownPosition() {
        if (unit.isVisible()) {
            return unit.getPosition();
        }
        return lastKnownLocation;
    }

    public void markCompleted(Time currentFrame) {
        if (completed) {
            return;
        }
        completed = true;
        completedFrame = currentFrame;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObservedUnit that = (ObservedUnit) o;
        return unit.equals(that.unit);
    }

    @Override
    public int hashCode() {
        return unit.hashCode();
    }
}
