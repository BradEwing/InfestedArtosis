package info.tracking;

import bwapi.PlayerType;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import util.Filter;
import util.StaticDefenseZone;
import util.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObservedUnitTracker {
    private static final int MARINE_COOLDOWN = 15;
    private static final int EMPTY_BUNKER_EVIDENCE_FRAMES = 48;

    private final HashMap<Unit, ObservedUnit> observedUnits = new HashMap<>();
    private final HashMap<Unit, BunkerGarrisonEstimator> bunkerGarrisons = new HashMap<>();
    private final EnemyReachMemory reachMemory = new EnemyReachMemory();

    public ObservedUnitTracker() {

    }

    /**
     * Refreshes every visible unit's hit points, shields and completion, and seeds the reach memory with the ground
     * range each visible unit's owner reports for its weapon.
     *
     * @param currentFrame current frame
     */
    public void onFrame(int currentFrame) {
        Time t = new Time(currentFrame);
        for (ObservedUnit ou : observedUnits.values()) {
            if (ou.getDestroyedFrame() != null) {
                continue;
            }
            Unit unit = ou.getUnit();
            if (unit.isVisible()) {
                if (unit.isCompleted()) {
                    ou.markCompleted(t);
                }
                ou.setLastKnownHitPoints(unit.getHitPoints());
                ou.setLastKnownShields(unit.getShields());
                if (ou.getUnitType() == UnitType.Terran_Bunker && ou.isCompleted()) {
                    ou.setLastLoadedCheckFrame(currentFrame);
                }
                seedReach(unit, currentFrame);
            }
        }
    }

    private void seedReach(Unit unit, int currentFrame) {
        WeaponType weapon = EnemyReachMemory.groundWeapon(unit.getType());
        if (weapon == WeaponType.None) {
            return;
        }
        reachMemory.seed(unit.getType(), unit.getPlayer().weaponMaxRange(weapon), currentFrame);
    }

    /**
     * Ground reach learned for every enemy type over the game, and the marks left by hits no known enemy
     * accounts for.
     *
     * @return the game-wide reach memory
     */
    public EnemyReachMemory getReachMemory() {
        return reachMemory;
    }

    /**
     * Zones around every tracked enemy army unit whose observation is fresh, each at its type's learned reach.
     *
     * @param isFresh whether an observation is recent enough to act on, given visibility, the frame it was last
     *     shown or hidden, and the current frame
     * @param isArmyType whether a type belongs to the enemy army
     * @param currentFrame current frame
     * @return one zone per fresh army unit with a known position
     */
    public List<StaticDefenseZone> getFreshArmyReachZones(FreshnessRule isFresh, Predicate<UnitType> isArmyType,
                                                          int currentFrame) {
        List<StaticDefenseZone> zones = new ArrayList<>();
        for (ObservedUnit ou : observedUnits.values()) {
            if (ou.getDestroyedFrame() != null || !isArmyType.test(ou.getUnitType())) {
                continue;
            }
            boolean visible = ou.getUnit().isVisible();
            if (!isFresh.test(visible, ou.getLastObservedFrame().getFrames(), currentFrame)) {
                continue;
            }
            Position position = ou.getCurrentOrLastKnownPosition();
            if (position == null) {
                continue;
            }
            zones.add(new StaticDefenseZone(ou.getUnitType(), position, reachMemory.groundReach(ou.getUnitType())));
        }
        return zones;
    }

    /**
     * Whether an observation is recent enough to act on.
     */
    @FunctionalInterface
    public interface FreshnessRule {
        boolean test(boolean visible, int lastObservedFrame, int currentFrame);
    }

    public void onUnitShow(Unit unit, int currentFrame, boolean isProxied) {
        Time t = new Time(currentFrame);
        if (!observedUnits.containsKey(unit)) {
            ObservedUnit ou = new ObservedUnit(unit, t, isProxied);
            if (unit.isCompleted()) {
                ou.markCompleted(t);
            }
            observedUnits.put(unit, ou);
        } else {
            ObservedUnit u = observedUnits.get(unit);
            u.setLastObservedFrame(t);
            u.setLastKnownLocation(unit.getPosition());
            updateUnitTypeChange(u, unit.getType());
            if (unit.isCompleted()) {
                u.markCompleted(t);
            }
        }
    }

    public void onUnitHide(Unit unit, int currentFrame) {
        Time t = new Time(currentFrame);
        if (observedUnits.containsKey(unit)) {
            ObservedUnit u = observedUnits.get(unit);
            u.setLastObservedFrame(t);
            u.setLastKnownLocation(unit.getPosition());
        }
    }

    public void onUnitDestroy(Unit unit, int currentFrame) {
        Time t = new Time(currentFrame);
        if (observedUnits.containsKey(unit)) {
            ObservedUnit u = observedUnits.get(unit);
            u.setDestroyedFrame(t);
            u.setLastKnownLocation(null);
        }
    }

    public int getUnitTypeCountBeforeTime(UnitType type, Time t) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType() == type)
                .filter(ou -> ou.getFirstObservedFrame().lessThanOrEqual(t))
                .count();
    }

    /**
     * Whether a unit of any of the given types was first observed at or before t, counting units since
     * destroyed. A unit that morphed is counted under the type it was last seen as.
     */
    public boolean hasObservedAnyBeforeTime(Time t, UnitType... types) {
        final Set<UnitType> typeSet = Arrays.stream(types).collect(Collectors.toSet());
        return observedUnits.values()
                .stream()
                .filter(ou -> typeSet.contains(ou.getUnitType()))
                .anyMatch(ou -> ou.getFirstObservedFrame().lessThanOrEqual(t));
    }

    /**
     * Whether a unit of a type the filter accepts has ever been observed, counting units since
     * destroyed. A unit that morphed is counted under the type it was last seen as.
     */
    public boolean hasObservedAny(Predicate<UnitType> typeFilter) {
        return observedUnits.values()
                .stream()
                .anyMatch(ou -> typeFilter.test(ou.getUnitType()));
    }

    public int getUnitTypeCountCompletedBeforeTime(UnitType type, Time t) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType() == type)
                .filter(ou -> ou.getCompletedFrame() != null && ou.getCompletedFrame().lessThanOrEqual(t))
                .count();
    }

    public int size() {
        return observedUnits.size();
    }

    public int getCountOfLivingUnits(UnitType unitType) {
        return getCountOfLivingUnits(type -> type == unitType);
    }

    public int getCountOfLivingUnits(UnitType... unitTypes) {
        final Set<UnitType> typeSet = Arrays.stream(unitTypes).collect(Collectors.toSet());
        return getCountOfLivingUnits(typeSet::contains);
    }

    public int getCountOfLivingUnits(Predicate<UnitType> typeFilter) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> typeFilter.test(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .count();
    }

    public boolean hasLivingGasBuilding() {
        return getCountOfLivingUnits(UnitType.Protoss_Assimilator, UnitType.Terran_Refinery, UnitType.Zerg_Extractor) > 0;
    }

    public int getCountOfAllEnemyUnits() {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> ou.getDestroyedFrame() == null)
                .count();
    }

    /**
     * Folds one frame of a visible bunker's fire into its garrison estimate, see {@link BunkerGarrisonEstimator}.
     *
     * @param bunker the bunker
     * @param newShots marine shots first seen this frame and attributed to the bunker
     * @param targetInRange whether one of our combat units was inside a marine's range of the bunker
     * @param currentFrame current frame
     */
    public void updateBunkerGarrison(Unit bunker, int newShots, boolean targetInRange, int currentFrame) {
        ObservedUnit ou = observedUnits.get(bunker);
        if (ou == null) return;
        BunkerGarrisonEstimator estimator = bunkerGarrisons.computeIfAbsent(bunker,
                b -> new BunkerGarrisonEstimator(MARINE_COOLDOWN, EMPTY_BUNKER_EVIDENCE_FRAMES));
        ou.setLastKnownLoadedCount(estimator.observe(newShots, targetInRange, currentFrame));
        ou.setLastBunkerBulletFrame(estimator.getLastShotFrame());
    }

    public void updateGroundHeight(Unit unit, int groundHeight) {
        ObservedUnit ou = observedUnits.get(unit);
        if (ou != null) {
            ou.setLastKnownGroundHeight(groundHeight);
        }
    }

    /**
     * Tracks an ObservedUnit built outside onUnitShow(), keyed on the unit it wraps.
     *
     * @param observedUnit the unit to track
     */
    void track(ObservedUnit observedUnit) {
        observedUnits.put(observedUnit.getUnit(), observedUnit);
    }

    /**
     * Retypes a tracked unit after a morph. The completion stamp describes the type that carried it, so a
     * change drops it and the unit is stamped again the next time it is observed complete.
     *
     * @param observedUnit the tracked unit
     * @param unitType the type the unit now has
     */
    static void updateUnitTypeChange(ObservedUnit observedUnit, UnitType unitType) {
        if (unitType == observedUnit.getUnitType()) {
            return;
        }
        observedUnit.setUnitType(unitType);
        observedUnit.resetCompletion();
    }

    public Set<Position> getLastKnownPositionsOfLivingUnits(UnitType unitType) {
        return knownPositionsOfLivingUnits(ou -> ou.getUnitType() == unitType);
    }

    public Set<Position> getLastKnownPositionsOfLivingUnits(UnitType... unitTypes) {
        final Set<UnitType> typeSet = Arrays.stream(unitTypes).collect(Collectors.toSet());
        return knownPositionsOfLivingUnits(ou -> typeSet.contains(ou.getUnitType()));
    }

    public int getProxiedCountByTypeBeforeTime(UnitType unitType, Time detectedBy) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType() == unitType)
                .filter(ObservedUnit::isProxied)
                .filter(ou -> ou.getFirstObservedFrame().lessThanOrEqual(detectedBy))
                .count();
    }

    /**
     * Last known positions of units of this type first observed at or before t. A unit whose position is no
     * longer known, including one since destroyed, is left out.
     */
    public Set<Position> getLastKnownPositionsObservedBeforeTime(UnitType unitType, Time t) {
        return knownPositions(observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType() == unitType)
                .filter(ou -> ou.getFirstObservedFrame().lessThanOrEqual(t))
                .map(ObservedUnit::getLastKnownLocation));
    }

    public Set<Unit> getDetectedUnits() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().isDetected())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    public Set<Unit> getBuilding() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    public Set<Unit> getCompletedBuildings() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ObservedUnit::isCompleted)
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    public Set<Position> getLastKnownPositionsOfBuildings() {
        return knownPositionsOfLivingUnits(ou -> ou.getUnitType().isBuilding());
    }

    private Set<Position> knownPositionsOfLivingUnits(Predicate<ObservedUnit> typeFilter) {
        return knownPositions(observedUnits.values()
                .stream()
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(typeFilter)
                .map(ObservedUnit::getCurrentOrLastKnownPosition));
    }

    /**
     * Single exit for every position query, so no caller can be handed the null that stands for an unknown
     * position.
     */
    static Set<Position> knownPositions(Stream<Position> positions) {
        return positions
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<Unit> getVisibleEnemyUnits() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().isVisible())
                .filter(ou -> ou.getUnit().getPlayer().getType() != PlayerType.None)
                .filter(ou -> ou.getUnit().getPlayer().getType() != PlayerType.Neutral)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    public Set<Unit> getHostileToGroundBuildings() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> Filter.isHostileBuildingToGround(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    public int getCompletedBuildingCountNearPositions(UnitType type, Set<Position> positions, int distance) {
        return (int) observedUnits.values().stream()
                .filter(ou -> ou.getUnitType() == type)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ObservedUnit::isCompleted)
                .filter(ou -> isNearAnyPosition(ou, positions, distance))
                .count();
    }

    public int getLivingBuildingCountNearPositions(Set<Position> positions, int distance) {
        return (int) observedUnits.values().stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> isNearAnyPosition(ou, positions, distance))
                .count();
    }

    private boolean isNearAnyPosition(ObservedUnit ou, Set<Position> positions, int distance) {
        Position unitPos = ou.getCurrentOrLastKnownPosition();
        if (unitPos == null) {
            return false;
        }
        for (Position pos : positions) {
            if (unitPos.getDistance(pos) <= distance) {
                return true;
            }
        }
        return false;
    }

    public Set<Unit> getProxiedBuildings() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ObservedUnit::isProxied)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    public Set<Unit> getWorkerUnitsNearPositions(Set<Position> positions, int distance) {
        return observedUnits.values()
                .stream()
                .filter(ou -> Filter.isWorkerType(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> isNearAnyPosition(ou, positions, distance))
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    /**
     * Last known positions of living enemy workers of any race inside an area, restricted to workers observed
     * recently. Reads only what was recorded when each worker was last shown or hidden, so a worker whose last
     * known position has been cleared by observation is left out.
     *
     * @param inArea tiles belonging to the area of interest
     * @param currentFrame current frame
     * @param maxAgeFrames most frames since a worker was last observed for it to count
     * @return the last known positions of qualifying workers
     */
    public Set<Position> getRecentWorkerPositionsIn(Predicate<TilePosition> inArea, int currentFrame,
                                                    int maxAgeFrames) {
        return knownPositions(observedUnits.values()
                .stream()
                .filter(ou -> Filter.isWorkerType(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> currentFrame - ou.getLastObservedFrame().getFrames() <= maxAgeFrames)
                .map(ObservedUnit::getLastKnownLocation)
                .filter(pos -> pos != null && inArea.test(pos.toTilePosition())));
    }

    public Position getLastKnownPosition(Unit unit) {
        ObservedUnit ou = observedUnits.get(unit);
        if (ou == null) {
            return null;
        }
        return ou.getCurrentOrLastKnownPosition();
    }

    public int getCountOfLivingUnitsOnTiles(UnitType unitType, Set<TilePosition> tiles) {
        return getCountOfLivingUnitsOnTiles(type -> type == unitType, tiles);
    }

    public int getCountOfLivingUnitsOnTiles(Predicate<UnitType> typeFilter, Set<TilePosition> tiles) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> typeFilter.test(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> {
                    Position pos = ou.getCurrentOrLastKnownPosition();
                    return pos != null && tiles.contains(pos.toTilePosition());
                })
                .count();
    }

    public int getCountOfVisibleUnitsOnTiles(Predicate<UnitType> typeFilter, Set<TilePosition> tiles) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> typeFilter.test(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> ou.getUnit().isVisible())
                .filter(ou -> tiles.contains(ou.getUnit().getPosition().toTilePosition()))
                .count();
    }

    public boolean hasLivingUnitAt(Predicate<UnitType> typeFilter, Predicate<TilePosition> tileFilter) {
        return observedUnits.values()
                .stream()
                .filter(ou -> typeFilter.test(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .anyMatch(ou -> {
                    Position pos = ou.getCurrentOrLastKnownPosition();
                    return pos != null && tileFilter.test(pos.toTilePosition());
                });
    }

    public Set<ObservedUnit> getLivingObservedUnits() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getDestroyedFrame() == null)
                .collect(Collectors.toSet());
    }

    /**
     * Forgets where a unit is once we can see it is no longer where we last saw it. The unit stays tracked
     * with an unknown position, which is why a last known position is nullable and readers go through
     * getCurrentOrLastKnownPosition() and knownPositions().
     *
     * @param visibleLocations positions we can currently see
     */
    public void clearLastKnownLocationsAt(Set<Position> visibleLocations) {
        if (visibleLocations == null || visibleLocations.isEmpty()) {
            return;
        }

        observedUnits.values()
                .stream()
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> ou.getLastKnownLocation() != null)
                .filter(ou -> visibleLocations.contains(ou.getLastKnownLocation()))
                .forEach(ou -> ou.setLastKnownLocation(null));
    }
}
