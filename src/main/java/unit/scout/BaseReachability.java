package unit.scout;

import bwapi.TilePosition;
import util.TileFootprint;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Whether known enemy buildings seal every ground route to a base. Nothing here touches {@code Game}: the caller
 * supplies tile step distances measured from our side of the map with those buildings blocked.
 */
public final class BaseReachability {

    /**
     * Tiles around a base's town hall location searched for one the walk reaches, so a single unwalkable tile at
     * the location itself does not read as a wall.
     */
    public static final int REACH_TILE_RADIUS = 1;

    private BaseReachability() {
    }

    /**
     * @param footprints tiles covered by known grounded enemy buildings
     * @return the set of those tiles
     */
    public static Set<TilePosition> blockedTiles(Collection<TileFootprint> footprints) {
        Set<TilePosition> tiles = new HashSet<>();
        for (TileFootprint footprint : footprints) {
            TilePosition topLeft = footprint.getTopLeft();
            for (int x = 0; x < footprint.getUnitType().tileWidth(); x++) {
                for (int y = 0; y < footprint.getUnitType().tileHeight(); y++) {
                    tiles.add(new TilePosition(topLeft.getX() + x, topLeft.getY() + y));
                }
            }
        }
        return tiles;
    }

    /**
     * A base is walled off when our own side is walkable and no tile within {@link #REACH_TILE_RADIUS} of the base
     * is reached by the walk. A walk that never started, or a base at the map edge with no tile to read, is not a
     * wall: nothing is known.
     *
     * @param stepDistances tile step distances from our side, indexed [x][y], -1 for a tile not reached
     * @param source the tile the walk started from
     * @param base the town hall location of the base
     * @return true when the walk started and reaches nowhere near the base
     */
    public static boolean isWalledOff(int[][] stepDistances, TilePosition source, TilePosition base) {
        if (!inRange(stepDistances, source) || stepDistances[source.getX()][source.getY()] != 0) {
            return false;
        }
        boolean anyTileRead = false;
        for (int x = base.getX() - REACH_TILE_RADIUS; x <= base.getX() + REACH_TILE_RADIUS; x++) {
            for (int y = base.getY() - REACH_TILE_RADIUS; y <= base.getY() + REACH_TILE_RADIUS; y++) {
                TilePosition tile = new TilePosition(x, y);
                if (!inRange(stepDistances, tile)) {
                    continue;
                }
                anyTileRead = true;
                if (stepDistances[x][y] >= 0) {
                    return false;
                }
            }
        }
        return anyTileRead;
    }

    private static boolean inRange(int[][] stepDistances, TilePosition tile) {
        return tile.getX() >= 0 && tile.getX() < stepDistances.length
                && tile.getY() >= 0 && tile.getY() < stepDistances[tile.getX()].length;
    }
}
