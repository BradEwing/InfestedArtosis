package info.tracking;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import lombok.Data;
import util.TileFootprint;
import util.Time;

@Data
public class ObservedUnit {
    /** A building last seen lifted this close to the spot it stood on, in pixels, still plugs that spot. */
    public static final int LIFTED_BLOCK_RADIUS_PIXELS = 192;

    private Time firstObservedFrame;
    /**
     * The first frame the unit was observed as its current type. A Drone first seen at 1:00 and next seen as a
     * Spawning Pool at 1:40 keeps 1:00 as its first observed frame, and holds 1:40 here.
     */
    private Time typeObservedFrame;
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
    private Position groundedAnchor;
    private boolean lastSeenLifted;

    public ObservedUnit(Unit unit, Time currentFrame, boolean proxied) {
        this(unit, unit.getType(), unit.getPosition(), currentFrame, proxied);
    }

    ObservedUnit(Unit unit, UnitType unitType, Position lastKnownLocation, Time currentFrame, boolean proxied) {
        this.unit = unit;
        this.unitType = unitType;
        this.firstObservedFrame = currentFrame;
        this.typeObservedFrame = currentFrame;
        this.lastObservedFrame = currentFrame;
        this.lastKnownLocation = lastKnownLocation;
        this.proxied = proxied;
        this.lastKnownHitPoints = unitType.maxHitPoints();
        this.lastKnownShields = unitType.maxShields();
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

    /**
     * Records whether the latest observation, the one that set the last known position, saw the unit lifted. The
     * first observation that is grounded and centred on its build tiles anchors the unit there; a building caught
     * off its build-tile centre, as a lifting or landing one is, does not anchor it.
     */
    public void recordLift(boolean lifted) {
        lastSeenLifted = lifted;
        if (groundedAnchor == null && !lifted && isOnBuildTileCentre(lastKnownLocation)) {
            groundedAnchor = lastKnownLocation;
        }
    }

    /**
     * Whether the latest observation saw the unit grounded on the build tiles of its first grounded sighting. A
     * Terran building that lifts and lands back in place counts again once it is seen landed; one last seen lifted,
     * or landed on other tiles, does not.
     */
    public boolean isGroundedAtAnchor() {
        if (groundedAnchor == null || lastSeenLifted || lastKnownLocation == null) {
            return false;
        }
        return buildTile(lastKnownLocation).equals(buildTile(groundedAnchor));
    }

    /**
     * Whether the building still plugs the ground it first stood on: it was last seen grounded there, or last seen
     * lifted within {@link #LIFTED_BLOCK_RADIUS_PIXELS} of that spot, where a lifted wall building hovers over the
     * gap it opened and lands back. One last seen lifted farther off, or landed on other tiles, does not.
     */
    public boolean blocksGroundAtAnchor() {
        if (isGroundedAtAnchor()) {
            return true;
        }
        return groundedAnchor != null && lastSeenLifted && lastKnownLocation != null
                && lastKnownLocation.getDistance(groundedAnchor) <= LIFTED_BLOCK_RADIUS_PIXELS;
    }

    private boolean isOnBuildTileCentre(Position position) {
        return position != null && new TileFootprint(unitType, buildTile(position)).centre().equals(position);
    }

    private TilePosition buildTile(Position position) {
        return TileFootprint.centredAt(unitType, position).getTopLeft();
    }

    public void markCompleted(Time currentFrame) {
        if (completed) {
            return;
        }
        completed = true;
        completedFrame = currentFrame;
    }

    /**
     * Drops the completion stamp. The stamp belongs to the type that carried it, so a unit that changes type
     * is unstamped until it is next observed complete as its new type.
     */
    public void resetCompletion() {
        completed = false;
        completedFrame = null;
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
