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
 * The ground a base occupies for observation purposes. Every test is bounded by distance, because BWEM
 * areas vary in size and a merged area can span far more ground than a base holds. A tile belongs to the
 * base when BWEM places it in the base's own area and it lies within areaTileRadius of the depot tile,
 * or when it lies within the tighter proximityTileRadius of the depot tile or of one of that area's
 * chokepoints. The chokepoint window covers buildings walling a choke from the far side, and the depot
 * window covers tiles BWEM maps to no area.
 */
public class BaseArea {

    private final TilePosition depotTile;
    private final Integer areaId;
    private final List<TilePosition> chokeTiles;
    private final int proximityTileRadius;
    private final int areaTileRadius;
    private final Function<TilePosition, Integer> areaIdAt;

    public BaseArea(TilePosition depotTile, Integer areaId, List<TilePosition> chokeTiles, int proximityTileRadius,
                    int areaTileRadius, Function<TilePosition, Integer> areaIdAt) {
        this.depotTile = depotTile;
        this.areaId = areaId;
        this.chokeTiles = chokeTiles;
        this.proximityTileRadius = proximityTileRadius;
        this.areaTileRadius = areaTileRadius;
        this.areaIdAt = areaIdAt;
    }

    public static BaseArea from(Base base, BWMap map, int proximityTileRadius, int areaTileRadius) {
        Area area = base.getArea();
        List<TilePosition> chokeTiles = new ArrayList<>();
        if (area != null) {
            for (ChokePoint choke : area.getChokePoints()) {
                chokeTiles.add(choke.getCenter().toTilePosition());
            }
        }
        return new BaseArea(base.getLocation(), areaId(area), chokeTiles, proximityTileRadius, areaTileRadius,
                tile -> areaIdAt(map, tile));
    }

    public boolean contains(TilePosition tile) {
        if (tile == null) {
            return false;
        }
        int toDepot = Distance.manhattanTileDistance(tile, depotTile);
        if (toDepot <= proximityTileRadius) {
            return true;
        }
        if (toDepot <= areaTileRadius && areaId != null && areaId.equals(areaIdAt.apply(tile))) {
            return true;
        }
        for (TilePosition choke : chokeTiles) {
            if (Distance.manhattanTileDistance(tile, choke) <= proximityTileRadius) {
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
