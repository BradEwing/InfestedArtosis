package util;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import lombok.Getter;

import java.util.function.Predicate;

/**
 * The tiles a building covers: the rectangle of its type's tile width and height anchored at its top-left tile.
 */
@Getter
public final class TileFootprint {

    private final UnitType unitType;
    private final TilePosition topLeft;

    public TileFootprint(UnitType unitType, TilePosition topLeft) {
        this.unitType = unitType;
        this.topLeft = topLeft;
    }

    /**
     * The footprint of a building reported at centre, the centre of its tile rectangle.
     */
    public static TileFootprint centredAt(UnitType unitType, Position centre) {
        int left = (centre.getX() - unitType.tileWidth() * 16) / 32;
        int top = (centre.getY() - unitType.tileHeight() * 16) / 32;
        return new TileFootprint(unitType, new TilePosition(left, top));
    }

    /**
     * The pixel centre of the rectangle, the inverse of {@link #centredAt(UnitType, Position)}.
     */
    public Position centre() {
        return new Position(topLeft.getX() * 32 + unitType.tileWidth() * 16,
                topLeft.getY() * 32 + unitType.tileHeight() * 16);
    }

    /**
     * The tile holding the centre of the rectangle, the tile a building's reported position rounds into.
     */
    public TilePosition centreTile() {
        return new TilePosition(topLeft.getX() + unitType.tileWidth() / 2, topLeft.getY() + unitType.tileHeight() / 2);
    }

    /**
     * Tiles between the two rectangles along the axis that separates them most, the larger of the horizontal and
     * the vertical gap: 0 when they overlap or touch along an edge or at a corner, 1 when a single row or column of
     * tiles lies between them or they sit one tile apart on both axes, diagonally.
     */
    public int tileGap(TileFootprint other) {
        int gapX = axisGap(topLeft.getX(), unitType.tileWidth(), other.topLeft.getX(), other.unitType.tileWidth());
        int gapY = axisGap(topLeft.getY(), unitType.tileHeight(), other.topLeft.getY(), other.unitType.tileHeight());
        return Math.max(gapX, gapY);
    }

    /**
     * Whether the tile lies inside the rectangle.
     */
    public boolean covers(TilePosition tile) {
        return tile.getX() >= topLeft.getX() && tile.getX() < topLeft.getX() + unitType.tileWidth()
                && tile.getY() >= topLeft.getY() && tile.getY() < topLeft.getY() + unitType.tileHeight();
    }

    /**
     * Whether any tile of the rectangle passes the tile test.
     */
    public boolean anyTile(Predicate<TilePosition> tileTest) {
        for (int x = topLeft.getX(); x < topLeft.getX() + unitType.tileWidth(); x++) {
            for (int y = topLeft.getY(); y < topLeft.getY() + unitType.tileHeight(); y++) {
                if (tileTest.test(new TilePosition(x, y))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int axisGap(int start, int length, int otherStart, int otherLength) {
        return Math.max(0, Math.max(start, otherStart) - Math.min(start + length, otherStart + otherLength));
    }
}
