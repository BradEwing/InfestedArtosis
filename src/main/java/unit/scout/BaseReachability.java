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
     * A base is walled off when the walk with no buildings blocked reaches it and the walk with the known
     * buildings blocked does not. A base the open walk cannot reach, an island or ground cut off by terrain, is
     * not walled: no building is to blame. A walk that never started is not a wall either: nothing is known.
     *
     * @param openDistances tile step distances from our side with nothing blocked, indexed [x][y], -1 for a tile
     *     not reached
     * @param blockedDistances the same with the known enemy buildings blocked
     * @param source the tile both walks started from
     * @param base the town hall location of the base
     * @return true when only the buildings keep the walk from the base
     */
    public static boolean isWalledOff(int[][] openDistances, int[][] blockedDistances, TilePosition source,
                                      TilePosition base) {
        if (!walkStarted(openDistances, source) || !walkStarted(blockedDistances, source)) {
            return false;
        }
        return reachesNear(openDistances, base) && !reachesNearOrOffMap(blockedDistances, base);
    }

    /**
     * Whether a unit standing on a tile is on ground the walk from our side reaches, as opposed to ground the
     * buildings cut off from it.
     *
     * @param stepDistances tile step distances from our side, indexed [x][y], -1 for a tile not reached
     * @param tile the unit's tile
     * @return true when the tile or a neighbour within {@link #REACH_TILE_RADIUS} is reached
     */
    public static boolean reachesNear(int[][] stepDistances, TilePosition tile) {
        for (int x = tile.getX() - REACH_TILE_RADIUS; x <= tile.getX() + REACH_TILE_RADIUS; x++) {
            for (int y = tile.getY() - REACH_TILE_RADIUS; y <= tile.getY() + REACH_TILE_RADIUS; y++) {
                if (inRange(stepDistances, new TilePosition(x, y)) && stepDistances[x][y] >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean reachesNearOrOffMap(int[][] stepDistances, TilePosition tile) {
        return reachesNear(stepDistances, tile) || !inRange(stepDistances, tile);
    }

    private static boolean walkStarted(int[][] stepDistances, TilePosition source) {
        return inRange(stepDistances, source) && stepDistances[source.getX()][source.getY()] == 0;
    }

    private static boolean inRange(int[][] stepDistances, TilePosition tile) {
        return tile.getX() >= 0 && tile.getX() < stepDistances.length
                && tile.getY() >= 0 && tile.getY() < stepDistances[tile.getX()].length;
    }
}
