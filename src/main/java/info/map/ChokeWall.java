package info.map;

import bwapi.TilePosition;
import lombok.Getter;
import util.TileFootprint;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The spots a building wall across a chokepoint must fill, at tile level. Where the choke runs over walkable,
 * non-buildable ground, a ramp or a bridge, the spots are the walkable, buildable tiles on one side that share an
 * edge with that ground. Where the choke's own tiles are buildable, the spots are those tiles. The choke is walled
 * when every spot is covered. Gaps narrower than a tile are not read.
 */
public final class ChokeWall {

    /**
     * Manhattan tiles from the choke's own tiles that the non-buildable ground behind it is followed, so the
     * whole length of a ramp is taken in while unbuildable ground further off is not.
     */
    static final int GAP_TILE_RADIUS = 6;

    @Getter
    private final Set<TilePosition> spots;

    private ChokeWall(Set<TilePosition> spots) {
        this.spots = Collections.unmodifiableSet(spots);
    }

    /**
     * The wall spots across a choke whose tiles are chokeTiles, on the side the side test accepts.
     */
    public static ChokeWall across(GameMap map, Collection<TilePosition> chokeTiles, Predicate<TilePosition> side) {
        Set<TilePosition> gap = gapTiles(map, chokeTiles);
        Set<TilePosition> spots = new HashSet<>();
        if (gap.isEmpty()) {
            for (TilePosition tile : chokeTiles) {
                if (isOpenBuildable(map, tile)) {
                    spots.add(tile);
                }
            }
            return new ChokeWall(spots);
        }
        for (TilePosition tile : gap) {
            for (TilePosition neighbour : cardinalNeighbours(tile)) {
                if (!gap.contains(neighbour) && isOpenBuildable(map, neighbour) && side.test(neighbour)) {
                    spots.add(neighbour);
                }
            }
        }
        return new ChokeWall(spots);
    }

    /**
     * Whether there are spots and the footprints cover every one of them.
     */
    public boolean isSealedBy(Collection<TileFootprint> footprints) {
        return !spots.isEmpty()
                && spots.stream().allMatch(spot -> footprints.stream().anyMatch(footprint -> footprint.covers(spot)));
    }

    private static Set<TilePosition> gapTiles(GameMap map, Collection<TilePosition> chokeTiles) {
        Map<TilePosition, Integer> depth = new HashMap<>();
        ArrayDeque<TilePosition> frontier = new ArrayDeque<>();
        for (TilePosition tile : chokeTiles) {
            if (isOpenUnbuildable(map, tile) && !depth.containsKey(tile)) {
                depth.put(tile, 0);
                frontier.add(tile);
            }
        }
        while (!frontier.isEmpty()) {
            TilePosition tile = frontier.poll();
            int next = depth.get(tile) + 1;
            if (next > GAP_TILE_RADIUS) {
                continue;
            }
            for (TilePosition neighbour : cardinalNeighbours(tile)) {
                if (!depth.containsKey(neighbour) && isOpenUnbuildable(map, neighbour)) {
                    depth.put(neighbour, next);
                    frontier.add(neighbour);
                }
            }
        }
        return depth.keySet();
    }

    private static TilePosition[] cardinalNeighbours(TilePosition tile) {
        return new TilePosition[] {
            new TilePosition(tile.getX() + 1, tile.getY()),
            new TilePosition(tile.getX() - 1, tile.getY()),
            new TilePosition(tile.getX(), tile.getY() + 1),
            new TilePosition(tile.getX(), tile.getY() - 1)
        };
    }

    private static boolean isOpenBuildable(GameMap map, TilePosition tile) {
        MapTile mapTile = mapTile(map, tile);
        return mapTile != null && mapTile.isWalkable() && mapTile.isBuildable();
    }

    private static boolean isOpenUnbuildable(GameMap map, TilePosition tile) {
        MapTile mapTile = mapTile(map, tile);
        return mapTile != null && mapTile.isWalkable() && !mapTile.isBuildable();
    }

    private static MapTile mapTile(GameMap map, TilePosition tile) {
        return map.isValidTile(tile) ? map.get(tile.getX(), tile.getY()) : null;
    }
}
