package info.map;

import bwapi.TilePosition;
import bwem.Area;
import bwem.BWMap;
import bwem.Base;
import bwem.ChokePoint;
import util.Distance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The ground a base occupies for observation purposes. A tile belongs to the base when BWEM places it in
 * the base's own area, when it lies within a manhattan radius of one of that area's chokepoints, or when
 * it lies within that radius of the depot tile. The chokepoint window covers buildings walling the choke
 * from the far side, and the depot window covers tiles BWEM maps to no area.
 */
public class BaseArea {

    private final TilePosition depotTile;
    private final Integer areaId;
    private final List<TilePosition> chokeTiles;
    private final int manhattanRadius;
    private final Function<TilePosition, Integer> areaIdAt;

    public BaseArea(TilePosition depotTile, Integer areaId, List<TilePosition> chokeTiles, int manhattanRadius,
                    Function<TilePosition, Integer> areaIdAt) {
        this.depotTile = depotTile;
        this.areaId = areaId;
        this.chokeTiles = chokeTiles;
        this.manhattanRadius = manhattanRadius;
        this.areaIdAt = areaIdAt;
    }

    public static BaseArea from(Base base, BWMap map, int manhattanRadius) {
        Area area = base.getArea();
        List<TilePosition> chokeTiles = new ArrayList<>();
        if (area != null) {
            for (ChokePoint choke : area.getChokePoints()) {
                chokeTiles.add(choke.getCenter().toTilePosition());
            }
        }
        return new BaseArea(base.getLocation(), areaId(area), chokeTiles, manhattanRadius, tile -> areaIdAt(map, tile));
    }

    public boolean contains(TilePosition tile) {
        if (tile == null) {
            return false;
        }
        if (areaId != null && areaId.equals(areaIdAt.apply(tile))) {
            return true;
        }
        if (Distance.manhattanTileDistance(tile, depotTile) <= manhattanRadius) {
            return true;
        }
        for (TilePosition choke : chokeTiles) {
            if (Distance.manhattanTileDistance(tile, choke) <= manhattanRadius) {
                return true;
            }
        }
        return false;
    }

    private static Integer areaIdAt(BWMap map, TilePosition tile) {
        if (!map.getData().getMapData().isValid(tile)) {
            return null;
        }
        return areaId(map.getArea(tile));
    }

    private static Integer areaId(Area area) {
        return area == null ? null : area.getId().intValue();
    }
}
