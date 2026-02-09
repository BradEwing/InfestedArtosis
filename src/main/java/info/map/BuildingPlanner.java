package info.map;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import bwem.ChokePoint;
import bwem.Geyser;
import bwem.Mineral;
import info.BaseData;
import util.TilePositionComparator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BuildingPlanner is responsible for finding the best, valid locations for buildings.
 * All BuildingPlans will call BuildingPlanner to identify a location for the building.
 */
public class BuildingPlanner {

    private Game game;
    private BWEM bwem;

    private HashSet<TilePosition> reservedTiles = new HashSet<>();

    public BuildingPlanner(Game game, BWEM bwem) {
        this.game = game;
        this.bwem = bwem;
    }

    public Set<TilePosition> getReservedTiles() {
        return new HashSet<>(reservedTiles);
    }

    public TilePosition getLocationForTechBuilding(Base base, UnitType unitType) {
        Position closestChoke = this.closestChokeToBase(base);
        Set<TilePosition> creepTiles = this.findSurroundingCreepTiles(base);
        TilePosition tileSize = unitType.tileSize();

        List<TilePosition> farthestFromChoke = new ArrayList<>(creepTiles);
        farthestFromChoke.sort(new TilePositionComparator(closestChoke.toTilePosition()));

        TilePosition best = null;
        for (TilePosition northWestCandidate: creepTiles) {
            TilePosition southEastCandidate = northWestCandidate.add(tileSize);
            if (!creepTiles.contains(southEastCandidate)) {
                continue;
            }

            if (!isValidBuildingLocation(northWestCandidate, tileSize, creepTiles)) {
                continue;
            }

            if (best == null) {
                best = northWestCandidate;
            }

            if (northWestCandidate.getDistance(closestChoke.toTilePosition()) > best.getDistance(closestChoke.toTilePosition())) {
                best = northWestCandidate;
            }
        }
        return best;
    }

    // reserveBuildingTiles is called when the building begins morphing/building
    public void reserveBuildingTiles(Unit unit) {
        TilePosition candidate = unit.getTilePosition();
        TilePosition tileSize = unit.getType().tileSize();
        for (int dx = 0; dx < tileSize.getX(); dx++) {
            for (int dy = 0; dy < tileSize.getY(); dy++) {
                TilePosition currentTile = candidate.add(new TilePosition(dx, dy));
                reservedTiles.add(currentTile);
            }
        }
    }

    // removeBuildingTiles is called when the building is destroyed
    public void removeBuildingTiles(Unit unit) {
        TilePosition candidate = unit.getTilePosition();
        TilePosition tileSize = unit.getType().tileSize();
        for (int dx = 0; dx < tileSize.getX(); dx++) {
            for (int dy = 0; dy < tileSize.getY(); dy++) {
                TilePosition currentTile = candidate.add(new TilePosition(dx, dy));
                reservedTiles.remove(currentTile);
            }
        }
    }

    private boolean isValidBuildingLocation(TilePosition candidate, TilePosition tileSize, Set<TilePosition> creepTiles) {
        for (int dx = 0; dx < tileSize.getX(); dx++) {
            for (int dy = 0; dy < tileSize.getY(); dy++) {
                TilePosition currentTile = candidate.add(new TilePosition(dx, dy));
                if (!creepTiles.contains(currentTile) || reservedTiles.contains(currentTile)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Position closestChokeToBase(Base base) {
        Position closestChoke = null;
        for (ChokePoint cp: bwem.getMap().getChokePoints()) {
            Position cpp = cp.getCenter().toPosition();
            if (closestChoke == null) {
                closestChoke = cpp;
                continue;
            }
            Position basePosition = base.getLocation().toPosition();
            if (basePosition.getDistance(cpp) < basePosition.getDistance(closestChoke)) {
                closestChoke = cpp;
            }
        }
        return closestChoke;
    }

    public HashSet<TilePosition> geyserBoundingBox(Base base) {
        TilePosition topLeft = null;
        TilePosition bottomRight = null;
        for (Geyser geyser : base.getGeysers()) {
            TilePosition geyserTopLeft = geyser.getTopLeft();
            TilePosition geyserBottomRight = geyser.getBottomRight();

            if (topLeft == null) {
                topLeft = geyserTopLeft;
            }
            if (bottomRight == null) {
                bottomRight = geyserBottomRight;
            }

            if (geyserTopLeft.getX() < topLeft.getX()) {
                topLeft = new TilePosition(geyserTopLeft.getX(), topLeft.getY());
            }
            if (geyserTopLeft.getY() > topLeft.getY()) {
                topLeft = new TilePosition(topLeft.getX(), geyserTopLeft.getY());
            }

            if (geyserBottomRight.getX() > bottomRight.getX()) {
                bottomRight = new TilePosition(geyserBottomRight.getX(), bottomRight.getY());
            }
            if (geyserBottomRight.getY() < bottomRight.getY()) {
                bottomRight = new TilePosition(bottomRight.getX(), geyserBottomRight.getY());
            }
        }

        if (topLeft == null || bottomRight == null) {
            return new HashSet<>();
        }

        TilePosition baseTopLeft      = base.getLocation();
        TilePosition baseBottomRight = baseTopLeft.add(new TilePosition(4, 3));

        int geyserMidX = (topLeft.getX()     + bottomRight.getX())     / 2;
        int geyserMidY = (topLeft.getY()     + bottomRight.getY())     / 2;
        int baseMidX   = (baseTopLeft.getX() + baseBottomRight.getX()) / 2;
        int baseMidY   = (baseTopLeft.getY() + baseBottomRight.getY()) / 2;

        int dx = geyserMidX - baseMidX;
        int dy = geyserMidY - baseMidY;

        if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) {
                topLeft    = new TilePosition(baseTopLeft.getX(), topLeft.getY());
            } else {
                bottomRight = new TilePosition(baseBottomRight.getX(), bottomRight.getY());
            }
        } else {
            if (dy > 0) {
                bottomRight = new TilePosition(bottomRight.getX(), baseBottomRight.getY());
            } else {
                topLeft     = new TilePosition(topLeft.getX(), baseTopLeft.getY());
            }
        }

        HashSet<TilePosition> boundingTiles = new HashSet<>();
        for (int x = topLeft.getX(); x <= bottomRight.getX(); x++) {
            for (int y = bottomRight.getY(); y <= topLeft.getY(); y++) {
                boundingTiles.add(new TilePosition(x, y));
            }
        }

        return boundingTiles;
    }

    public HashSet<TilePosition> mineralBoundingBox(Base base) {
        TilePosition topLeft = null;
        TilePosition bottomRight = null;
        for (Mineral mineral: base.getMinerals()) {
            TilePosition mineralTopLeft = mineral.getTopLeft();
            TilePosition mineralBottomRight = mineral.getBottomRight();
            if (topLeft == null) {
                topLeft = mineralTopLeft;
            }
            if (bottomRight == null) {
                bottomRight = mineralBottomRight;
            }

            if (mineralTopLeft.getX() < topLeft.getX()) {
                topLeft = new TilePosition(mineralTopLeft.getX(), topLeft.getY());
            }
            if (mineralTopLeft.getY() > topLeft.getY()) {
                topLeft = new TilePosition(topLeft.getX(), mineralTopLeft.getY());
            }

            if (mineralBottomRight.getX() > bottomRight.getX()) {
                bottomRight = new TilePosition(mineralBottomRight.getX(), bottomRight.getY());
            }
            if (mineralBottomRight.getY() < bottomRight.getY()) {
                bottomRight = new TilePosition(bottomRight.getX(), mineralBottomRight.getY());
            }
        }

        TilePosition baseTopLeft = base.getLocation();
        TilePosition baseBottomRight = baseTopLeft.add(new TilePosition(4, 3));

        int mineralMidX = (topLeft.getX()     + bottomRight.getX()) / 2;
        int mineralMidY = (topLeft.getY()     + bottomRight.getY()) / 2;
        int baseMidX = (baseTopLeft.getX() + baseBottomRight.getX()) / 2;
        int baseMidY = (baseTopLeft.getY() + baseBottomRight.getY()) / 2;
        int dx = mineralMidX - baseMidX;
        int dy = mineralMidY - baseMidY;

        if (Math.abs(dx) > Math.abs(dy)) {
            if (dx > 0) {
                topLeft = new TilePosition(baseTopLeft.getX(), topLeft.getY());
            } else {
                bottomRight = new TilePosition(baseBottomRight.getX(), bottomRight.getY());
            }
        } else {
            if (dy > 0) {
                bottomRight = new TilePosition(bottomRight.getX(), baseBottomRight.getY());
            } else {
                topLeft = new TilePosition(topLeft.getX(), baseTopLeft.getY());
            }
        }

        HashSet<TilePosition> boundingTiles = new HashSet<>();
        for (int x = topLeft.getX(); x <= bottomRight.getX(); x++) {
            for (int y = bottomRight.getY(); y <= topLeft.getY(); y++) {
                boundingTiles.add(new TilePosition(x, y));
            }
        }

        return boundingTiles;
    }

    public Set<TilePosition> findSurroundingCreepTiles(Base base) {
        Set<TilePosition> creepTiles = new HashSet<>();
        HashSet<TilePosition> checked = new HashSet<>();
        HashSet<TilePosition> mineralExcluded = mineralBoundingBox(base);
        HashSet<TilePosition> geyserExcluded = geyserBoundingBox(base);
        Queue<TilePosition> candidates = new LinkedList<>();
        TilePosition tileSize = new TilePosition(6, 5);
        TilePosition baseLocation = base.getLocation();
        for (int x = -1; x < tileSize.getX(); x++) {
            for (int y = -1; y < tileSize.getX(); y++) {
                candidates.add(baseLocation.add(new TilePosition(x, y)));
            }
        }

        while (!candidates.isEmpty()) {
            TilePosition current = candidates.poll();

            if (checked.contains(current)) {
                continue;
            }
            checked.add(current);

            if (game.hasCreep(current)) {
                creepTiles.add(current);

                // Enqueue all 8 neighboring tiles
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int newX = current.getX() + dx;
                        int newY = current.getY() + dy;

                        // Check map bounds
                        if (newX >= 0 && newX < game.mapWidth() && newY >= 0 && newY < game.mapHeight()) {
                            TilePosition neighbor = new TilePosition(newX, newY);
                            if (!checked.contains(neighbor)) {
                                candidates.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        creepTiles = creepTiles.stream()
                .filter(t -> !mineralExcluded.contains(t) && !geyserExcluded.contains(t))
                .collect(Collectors.toSet());

        return creepTiles;
    }

    /**
     * Pick a TilePosition to place a new creep colony (for later morphing into a Sunken Colony),
     * built toward the base's closest choke.  If there's existing reserved structures, attempts to build adjacent.
     */
    public TilePosition getLocationForCreepColony(Base base, Race opponentRace) {
        Position chokeCenter = closestChokeToBase(base);
        Set<TilePosition> creepTiles = findSurroundingCreepTiles(base);
        TilePosition colonySize = UnitType.Zerg_Creep_Colony.tileSize();

        List<TilePosition> candidates = new ArrayList<>();
        for (TilePosition tp : creepTiles) {
            TilePosition se = tp.add(colonySize);
            if (!creepTiles.contains(se)) continue;
            if (!isValidBuildingLocation(tp, colonySize, creepTiles)) continue;
            candidates.add(tp);
        }

        Set<TilePosition> existing = new HashSet<>(reservedTiles);
        existing.retainAll(creepTiles);

        if (!existing.isEmpty()) {
            List<TilePosition> adjacent = new ArrayList<>();
            for (TilePosition cand : candidates) {
                for (TilePosition res : existing) {
                    if (res.getApproxDistance(cand) <= 23) {
                        adjacent.add(cand);
                        break;
                    }
                }
            }
            if (!adjacent.isEmpty()) {
                candidates = adjacent;
            }
        }

        candidates.sort(new TilePositionComparator(chokeCenter.toTilePosition()));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Finds a location for a macro hatchery based on opponent race and existing macro hatchery count.
     *
     * @param opponentRace the race of the opponent
     * @param baseData BaseData instance to access base information
     * @return TilePosition for macro hatchery placement, or null if no suitable location found
     */
    public TilePosition getLocationForMacroHatchery(Race opponentRace, BaseData baseData) {
        Base targetBase = determineTargetBaseForMacroHatch(opponentRace, baseData);
        if (targetBase == null) {
            return null;
        }

        return findBuildableLocationNearBase(targetBase);
    }

    /**
     * Determines which base should receive the next macro hatchery based on opponent race and count.
     */
    private Base determineTargetBaseForMacroHatch(Race opponentRace, BaseData baseData) {
        Base mainBase = baseData.getMainBase();
        Base naturalBase = baseData.hasNaturalExpansion() ?
                baseData.baseAtTilePosition(baseData.naturalExpansionPosition()) : null;
        Base thirdBase = findThirdBase(baseData);
        int existingMacroHatchCount = baseData.numMacroHatcheries();

        if (opponentRace == Race.Terran) {
            switch (existingMacroHatchCount) {
                case 0:
                    return mainBase;
                case 1:
                    return naturalBase;
                default:
                    return thirdBase;
            }
        } else if (opponentRace == Race.Protoss) {
            switch (existingMacroHatchCount) {
                case 0:
                    return naturalBase;
                case 1:
                case 2:
                    return thirdBase;
                default:
                    return mainBase;
            }
        } else {
            switch (existingMacroHatchCount) {
                case 0:
                    return mainBase;
                case 1:
                    return naturalBase;
                default:
                    return thirdBase;
            }
        }
    }

    /**
     * Finds the third base (closest to main base that isn't main or natural).
     */
    private Base findThirdBase(BaseData baseData) {
        Base mainBase = baseData.getMainBase();
        Base naturalBase = baseData.hasNaturalExpansion() ?
                baseData.baseAtTilePosition(baseData.naturalExpansionPosition()) : null;

        HashSet<Base> myBases = baseData.getMyBases();
        Base closestBase = null;
        double closestDistance = Double.MAX_VALUE;

        for (Base base : myBases) {
            if (base == mainBase || base == naturalBase) {
                continue;
            }

            double distance = mainBase.getLocation().getDistance(base.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestBase = base;
            }
        }

        return closestBase;
    }

    /**
     * Finds a buildable location near the target base for the specified building type.
     */
    private TilePosition findBuildableLocationNearBase(Base base) {
        TilePosition baseLocation = base.getLocation();
        TilePosition buildingSize = UnitType.Zerg_Hatchery.tileSize();

        // Search in expanding circles around the base location
        for (int radius = 1; radius <= 10; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
                        continue;
                    }

                    TilePosition candidate = baseLocation.add(new TilePosition(dx, dy));

                    if (isValidMacroHatchLocation(candidate, buildingSize, base)) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks if a location is valid for placing a macro hatchery.
     */
    private boolean isValidMacroHatchLocation(TilePosition location, TilePosition buildingSize, Base base) {
        if (location.getX() < 0 || location.getY() < 0 ||
                location.getX() + buildingSize.getX() >= game.mapWidth() ||
                location.getY() + buildingSize.getY() >= game.mapHeight()) {
            return false;
        }

        for (int dx = 0; dx < buildingSize.getX(); dx++) {
            for (int dy = 0; dy < buildingSize.getY(); dy++) {
                TilePosition currentTile = location.add(new TilePosition(dx, dy));

                if (!game.isBuildable(currentTile) || reservedTiles.contains(currentTile)) {
                    return false;
                }
            }
        }

        if (!isValidDistanceFromBaseHatchery(location, buildingSize, base)) {
            return false;
        }

        if (!isValidDistanceFromResources(location, buildingSize, base)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if the macro hatchery location is at least 5 tiles away from all minerals and geysers.
     */
    private boolean isValidDistanceFromResources(TilePosition hatchLocation, TilePosition buildingSize, Base base) {
        final int MIN_DISTANCE = 5;

        for (Mineral mineral : base.getMinerals()) {
            TilePosition mineralPos = mineral.getTopLeft();
            int minDistance = calculateMinManhattanDistance(hatchLocation, buildingSize, mineralPos, new TilePosition(2, 1));
            if (minDistance < MIN_DISTANCE) {
                return false;
            }
        }

        for (Geyser geyser : base.getGeysers()) {
            TilePosition geyserPos = geyser.getTopLeft();
            int minDistance = calculateMinManhattanDistance(hatchLocation, buildingSize, geyserPos, new TilePosition(4, 2));
            if (minDistance < MIN_DISTANCE) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the macro hatchery location doesn't overlap with the base hatchery.
     */
    private boolean isValidDistanceFromBaseHatchery(TilePosition hatchLocation, TilePosition buildingSize, Base base) {
        TilePosition baseLocation = base.getLocation();
        TilePosition baseSize = new TilePosition(4, 3);

        int distance = calculateMinManhattanDistance(hatchLocation, buildingSize, baseLocation, baseSize);
        return distance > 0;
    }

    /**
     * Calculates the minimum Manhattan distance between two rectangular areas.
     */
    private int calculateMinManhattanDistance(TilePosition pos1, TilePosition size1, TilePosition pos2, TilePosition size2) {
        // Calculate the closest points between the two rectangles
        int x1_min = pos1.getX();
        int x1_max = pos1.getX() + size1.getX() - 1;
        int y1_min = pos1.getY();
        int y1_max = pos1.getY() + size1.getY() - 1;

        int x2_min = pos2.getX();
        int x2_max = pos2.getX() + size2.getX() - 1;
        int y2_min = pos2.getY();
        int y2_max = pos2.getY() + size2.getY() - 1;

        // Calculate minimum distance in each dimension
        int dx = 0;
        if (x1_max < x2_min) {
            dx = x2_min - x1_max;
        } else if (x2_max < x1_min) {
            dx = x1_min - x2_max;
        }

        int dy = 0;
        if (y1_max < y2_min) {
            dy = y2_min - y1_max;
        } else if (y2_max < y1_min) {
            dy = y1_min - y2_max;
        }

        return dx + dy;
    }

    /**
     * Reserves building tiles for a planned building based on position and unit type.
     * Called when a build position is assigned to a plan to prevent location conflicts.
     *
     * @param buildPosition The top-left tile position where the building will be placed
     * @param unitType The type of building that will be constructed
     */
    public void reservePlannedBuildingTiles(TilePosition buildPosition, UnitType unitType) {
        if (buildPosition == null || unitType == null) {
            return;
        }

        TilePosition tileSize = unitType.tileSize();
        for (int dx = 0; dx < tileSize.getX(); dx++) {
            for (int dy = 0; dy < tileSize.getY(); dy++) {
                TilePosition currentTile = buildPosition.add(new TilePosition(dx, dy));
                reservedTiles.add(currentTile);
            }
        }
    }

    /**
     * Unreserves building tiles for a planned building based on position and unit type.
     * Called when a plan is cancelled or reassigned to free up the location.
     *
     * @param buildPosition The top-left tile position that was reserved
     * @param unitType The type of building that was planned
     */
    public void unreservePlannedBuildingTiles(TilePosition buildPosition, UnitType unitType) {
        if (buildPosition == null || unitType == null) {
            return;
        }

        TilePosition tileSize = unitType.tileSize();
        for (int dx = 0; dx < tileSize.getX(); dx++) {
            for (int dy = 0; dy < tileSize.getY(); dy++) {
                TilePosition currentTile = buildPosition.add(new TilePosition(dx, dy));
                reservedTiles.remove(currentTile);
            }
        }
    }
}
