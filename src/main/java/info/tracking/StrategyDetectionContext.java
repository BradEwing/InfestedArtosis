package info.tracking;

import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.Area;
import bwem.BWMap;
import bwem.Base;
import info.BaseData;
import info.map.BaseArea;
import info.map.GameMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.Time;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@RequiredArgsConstructor
public class StrategyDetectionContext {
    @Getter
    private final ObservedUnitTracker tracker;
    @Getter
    private final Time time;
    @Getter
    private final BaseData baseData;
    @Getter
    private final GameMap gameMap;
    private final BWMap bwMap;

    private final Map<Integer, Set<TilePosition>> ourBaseTilesByNaturalRadius = new HashMap<>();

    /**
     * Tiles of our main base plus a manhattan radius around our inferred natural.
     * Cached per context instance so detectors sharing a frame do not recompute it.
     */
    public Set<TilePosition> ourBaseTiles(int naturalTileRadius) {
        return ourBaseTilesByNaturalRadius.computeIfAbsent(naturalTileRadius, this::computeOurBaseTiles);
    }

    /**
     * Ground belonging to the inferred enemy natural: its depot tile and the chokepoints of its area
     * widened by proximityTileRadius, plus tiles BWEM places in that area within areaTileRadius of the
     * depot. Null while the enemy natural is unknown.
     */
    public BaseArea enemyNaturalArea(int proximityTileRadius, int areaTileRadius) {
        Base enemyNatural = baseData.getEnemyNaturalBase();
        if (enemyNatural == null) {
            return null;
        }
        return BaseArea.from(enemyNatural, bwMap, proximityTileRadius, areaTileRadius);
    }

    /**
     * Whether a living enemy Hatchery, Lair or Hive, other than the depot on the enemy main's base location,
     * stands in the BWEM Area of the enemy main. False while the enemy main is unknown.
     */
    public boolean enemyHasExtraDepotInMainArea() {
        Base enemyMain = baseData.getMainEnemyBase();
        if (enemyMain == null || enemyMain.getArea() == null) {
            return false;
        }
        TilePosition mainLocation = enemyMain.getLocation();
        return hasEnemyDepotInArea(enemyMain.getArea(), tile -> !occupiesBaseLocation(tile, mainLocation));
    }

    /**
     * Whether a living enemy Hatchery, Lair or Hive stands in the BWEM Area of the inferred enemy natural.
     * False while the enemy natural is unknown.
     */
    public boolean enemyNaturalHasDepot() {
        Base enemyNatural = baseData.getEnemyNaturalBase();
        if (enemyNatural == null || enemyNatural.getArea() == null) {
            return false;
        }
        return hasEnemyDepotInArea(enemyNatural.getArea(), tile -> true);
    }

    /**
     * Whether a depot reported at depotTile stands on the base whose location is baseLocation: the tile lies
     * inside the Hatchery footprint anchored at that location. depotTile is the tile of the depot's reported
     * centre position, so the test holds whichever tile of the footprint the centre rounds into, and a second
     * depot never passes because buildings cannot overlap the footprint.
     */
    static boolean occupiesBaseLocation(TilePosition depotTile, TilePosition baseLocation) {
        int dx = depotTile.getX() - baseLocation.getX();
        int dy = depotTile.getY() - baseLocation.getY();
        return dx >= 0 && dx < UnitType.Zerg_Hatchery.tileWidth()
                && dy >= 0 && dy < UnitType.Zerg_Hatchery.tileHeight();
    }

    private boolean hasEnemyDepotInArea(Area area, Predicate<TilePosition> depotFilter) {
        return tracker.hasLivingUnitAt(StrategyDetectionContext::isZergDepot,
                tile -> depotFilter.test(tile) && isInArea(tile, area));
    }

    private static boolean isZergDepot(UnitType unitType) {
        return unitType == UnitType.Zerg_Hatchery || unitType == UnitType.Zerg_Lair || unitType == UnitType.Zerg_Hive;
    }

    private boolean isInArea(TilePosition tile, Area area) {
        if (!bwMap.getData().getMapData().isValid(tile)) {
            return false;
        }
        Area tileArea = bwMap.getArea(tile);
        return tileArea != null && tileArea.getId().equals(area.getId());
    }

    private Set<TilePosition> computeOurBaseTiles(int naturalTileRadius) {
        return baseData.ourBaseTiles(gameMap, naturalTileRadius);
    }
}
