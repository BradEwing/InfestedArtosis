package info.tracking;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.Area;
import bwem.BWMap;
import bwem.Base;
import info.BaseData;
import info.ScoutData;
import info.map.BaseArea;
import info.map.GameMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.Distance;
import util.Time;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.stream.Collectors;

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
    private final ScoutData scoutData;

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
     * Tiles within tileRadius manhattan tiles of the centre of a chokepoint of the enemy main's BWEM Area, the
     * ground a wall across the main's ramp or entrance stands on. Null while the enemy main is unknown.
     */
    public Predicate<TilePosition> enemyMainChokeArea(int tileRadius) {
        Base enemyMain = baseData.getMainEnemyBase();
        if (enemyMain == null || enemyMain.getArea() == null) {
            return null;
        }
        List<TilePosition> chokeTiles = enemyMain.getArea().getChokePoints().stream()
                .map(choke -> choke.getCenter().toTilePosition())
                .collect(Collectors.toList());
        return tile -> isWithinTileRadius(tile, chokeTiles, tileRadius);
    }

    /**
     * Whether the tile lies within tileRadius manhattan tiles of any of the centres.
     */
    static boolean isWithinTileRadius(TilePosition tile, Collection<TilePosition> centres, int tileRadius) {
        return centres.stream().anyMatch(centre -> Distance.manhattanTileDistance(tile, centre) <= tileRadius);
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
     * The first frame our vision had covered the enemy main as ScoutData.getEnemyMainScoutedFrame defines it.
     * Null while the enemy main is unknown or not yet scouted.
     */
    public Time enemyMainScoutedFrame() {
        Base enemyMain = baseData.getMainEnemyBase();
        if (enemyMain == null) {
            return null;
        }
        return scoutData.getEnemyMainScoutedFrame(enemyMain);
    }

    /**
     * Whether the position is on our side of the map: its BWEM ground path to our main is shorter than its
     * path to the enemy main or, while the enemy main is unknown, to every other starting location not yet seen
     * empty. When every other starting location has been seen empty, as after the enemy main is razed, it is
     * measured against all of them, so the test never passes on an empty set of enemy mains.
     */
    public boolean isOnOurSide(Position position) {
        return isOnOurSide(position, baseData, bwMap.getBases(), bwMap::getPathLength);
    }

    /**
     * {@link #isOnOurSide(Position)} over the given bases, measuring ground paths with pathLength.
     */
    public static boolean isOnOurSide(Position position, BaseData baseData, Collection<Base> bases,
                                      ToIntBiFunction<Position, Position> pathLength) {
        Base ourMain = baseData.getMainBase();
        if (ourMain == null) {
            return false;
        }
        int ourLength = pathLength.applyAsInt(position, ourMain.getCenter());
        List<Integer> enemyLengths = enemyMainCandidates(baseData, bases).stream()
                .map(base -> pathLength.applyAsInt(position, base.getCenter()))
                .collect(Collectors.toList());
        return isCloserToOurMain(ourLength, enemyLengths);
    }

    /**
     * Whether a ground path of ourLength is shorter than every enemy path. A negative length means BWEM found
     * no ground path: with none to our main the position is not on our side, and an enemy main with none does
     * not count against it.
     */
    static boolean isCloserToOurMain(int ourLength, Collection<Integer> enemyLengths) {
        if (ourLength < 0) {
            return false;
        }
        return enemyLengths.stream().allMatch(length -> length < 0 || ourLength < length);
    }

    private static List<Base> enemyMainCandidates(BaseData baseData, Collection<Base> bases) {
        Base enemyMain = baseData.getMainEnemyBase();
        if (enemyMain != null) {
            return Collections.singletonList(enemyMain);
        }
        List<Base> otherStarts = bases.stream()
                .filter(Base::isStartingLocation)
                .filter(base -> base != baseData.getMainBase())
                .collect(Collectors.toList());
        List<Base> unresolvedStarts = otherStarts.stream()
                .filter(base -> !baseData.isStartSeenEmpty(base))
                .collect(Collectors.toList());
        return unresolvedStarts.isEmpty() ? otherStarts : unresolvedStarts;
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
