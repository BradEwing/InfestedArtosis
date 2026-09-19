package util;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

/**
 * Ground a static defence structure can fire on. Weapon range is measured from the structure's edge, the way
 * Unit.getDistance measures it, so the zone is the structure's footprint grown by its reach.
 */
public final class StaticDefenseZone {

    private static final int GRID_STEP = 8;

    @Getter private final UnitType structure;
    @Getter private final Position center;
    @Getter private final int reach;

    public StaticDefenseZone(UnitType structure, Position center, int reach) {
        this.structure = structure;
        this.center = center;
        this.reach = reach;
    }

    /**
     * Distance from a point to the nearest edge of the structure's footprint, or 0 when the point is inside it.
     *
     * @param x pixel x
     * @param y pixel y
     * @return distance in pixels from the footprint's edge
     */
    public double edgeDistance(int x, int y) {
        int left = center.getX() - structure.dimensionLeft();
        int right = center.getX() + structure.dimensionRight();
        int top = center.getY() - structure.dimensionUp();
        int bottom = center.getY() + structure.dimensionDown();
        int dx = Math.max(0, Math.max(left - x, x - right));
        int dy = Math.max(0, Math.max(top - y, y - bottom));
        return Math.sqrt((double) dx * dx + (double) dy * dy);
    }

    /**
     * Whether a unit standing at a point could be fired on.
     *
     * @param position position of the unit's center
     * @param padding pixels added to the reach, covering the unit's own extent and any safety margin
     * @return true when the point is within reach plus padding of the structure's edge
     */
    public boolean covers(Position position, int padding) {
        return edgeDistance(position.getX(), position.getY()) <= reach + padding;
    }

    /**
     * Grid points the zone covers, aligned to multiples of the grid step.
     *
     * @return every covered grid point
     */
    public Set<Position> coveredGridPositions() {
        Set<Position> covered = new HashSet<>();
        int extentX = Math.max(structure.dimensionLeft(), structure.dimensionRight()) + reach;
        int extentY = Math.max(structure.dimensionUp(), structure.dimensionDown()) + reach;
        int startX = Math.floorDiv(center.getX() - extentX, GRID_STEP) * GRID_STEP;
        int startY = Math.floorDiv(center.getY() - extentY, GRID_STEP) * GRID_STEP;
        for (int x = startX; x <= center.getX() + extentX; x += GRID_STEP) {
            for (int y = startY; y <= center.getY() + extentY; y += GRID_STEP) {
                Position point = new Position(x, y);
                if (covers(point, 0)) {
                    covered.add(point);
                }
            }
        }
        return covered;
    }
}
