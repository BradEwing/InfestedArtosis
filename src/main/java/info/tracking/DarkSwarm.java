package info.tracking;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;

/**
 * One active Dark Swarm, read from its Spell_Dark_Swarm unit on the frame it was last seen.
 *
 * <p>The footprint is the Spell_Dark_Swarm unit's own box, taken from the {@link UnitType} dimensions rather than a
 * radius. A unit is under the swarm when its own box overlaps that footprint, so both boxes are compared edge to
 * edge. Covering a position does not by itself protect what stands there: flyers and buildings get no cover, and
 * the swarm negates only ordinary ranged direct attacks.
 */
@Getter
public final class DarkSwarm {

    private static final UnitType SWARM = UnitType.Spell_Dark_Swarm;

    private final int id;
    private final Position center;
    private final int remainingFrames;

    /**
     * @param id the Spell_Dark_Swarm unit's id
     * @param center the Spell_Dark_Swarm unit's position
     * @param remainingFrames frames until the swarm is removed, as {@code Unit.getRemoveTimer()} reports it
     */
    public DarkSwarm(int id, Position center, int remainingFrames) {
        this.id = id;
        this.center = center;
        this.remainingFrames = remainingFrames;
    }

    public int left() {
        return center.getX() - SWARM.dimensionLeft();
    }

    public int right() {
        return center.getX() + SWARM.dimensionRight();
    }

    public int top() {
        return center.getY() - SWARM.dimensionUp();
    }

    public int bottom() {
        return center.getY() + SWARM.dimensionDown();
    }

    /**
     * Whether the box of a unit of the given type standing at the given position overlaps the footprint.
     *
     * @param position the unit's position
     * @param type the unit's type, whose dimensions give its box
     * @return true when the two boxes share at least one pixel
     */
    public boolean overlaps(Position position, UnitType type) {
        return gap(position, type) <= 0;
    }

    /**
     * Distance between the box of a unit of the given type at the given position and the footprint, edge to edge.
     *
     * @param position the unit's position
     * @param type the unit's type, whose dimensions give its box
     * @return 0 when the boxes overlap, otherwise the straight line gap in pixels
     */
    public double gap(Position position, UnitType type) {
        int dx = axisGap(position.getX() - type.dimensionLeft(), position.getX() + type.dimensionRight(),
                left(), right());
        int dy = axisGap(position.getY() - type.dimensionUp(), position.getY() + type.dimensionDown(),
                top(), bottom());
        return Math.hypot(dx, dy);
    }

    /**
     * Distance from a point to the footprint.
     *
     * @param point the point
     * @return 0 inside the footprint, otherwise the straight line gap in pixels
     */
    public double gap(Position point) {
        int dx = axisGap(point.getX(), point.getX(), left(), right());
        int dy = axisGap(point.getY(), point.getY(), top(), bottom());
        return Math.hypot(dx, dy);
    }

    private static int axisGap(int low, int high, int footprintLow, int footprintHigh) {
        if (high < footprintLow) {
            return footprintLow - high;
        }
        if (low > footprintHigh) {
            return low - footprintHigh;
        }
        return 0;
    }
}
