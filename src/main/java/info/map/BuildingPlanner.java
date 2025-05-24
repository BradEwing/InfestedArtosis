package info.map;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import bwem.ChokePoint;
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
    private GameMap gameMap;

    private HashSet<TilePosition> reservedTiles = new HashSet<>();

    public BuildingPlanner(Game game, BWEM bwem, GameMap gameMap) {
        this.game = game;
        this.bwem = bwem;
        this.gameMap = gameMap;
    }

    public void debugBaseCreepTiles(Base base) {
        HashSet<TilePosition> creepTiles = this.findSurroundingCreepTiles(base);
        for (TilePosition tp: creepTiles) {
            game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.Brown);
        }
    }

    public void debugBaseChoke(Base base) {
        HashSet<TilePosition> creepTiles = this.findSurroundingCreepTiles(base);
        Position closestChoke = this.closestChokeToBase(base);
        game.drawCircleMap(closestChoke, 2, Color.Yellow);
        List<TilePosition> farthestFromChoke = creepTiles.stream().collect(Collectors.toList());
        farthestFromChoke.sort(new TilePositionComparator(closestChoke.toTilePosition()));

//        for (TilePosition tp: farthestFromChoke) {
//            double distance = tp.toPosition().getDistance(closestChoke);
//            game.drawTextMap(tp.toPosition(), String.valueOf((int) distance), Text.White);
//        }
    }

    public void debugLocationForTechBuilding(Base base, UnitType unitType) {
        if (base == null) {
            return;
        }
        TilePosition tp = this.getLocationForTechBuilding(base, unitType);
        game.drawBoxMap(tp.toPosition(), tp.add(unitType.tileSize()).toPosition(), Color.White);
    }

    public void debugReserveTiles() {
        for (TilePosition tp: reservedTiles) {
            game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.White);
        }
    }

    public void debugNextCreepColonyLocation(Base base) {
        TilePosition cc = getLocationForCreepColony(base);
        if (cc != null) {
            game.drawBoxMap(cc.toPosition(), cc.add(new TilePosition(2, 2)).toPosition(), Color.White);
        }
    }

    public TilePosition getLocationForTechBuilding(Base base, UnitType unitType) {
        Position closestChoke = this.closestChokeToBase(base);
        HashSet<TilePosition> creepTiles = this.findSurroundingCreepTiles(base);
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

    private boolean isValidBuildingLocation(TilePosition candidate, TilePosition tileSize, HashSet<TilePosition> creepTiles) {
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

    private Position closestChokeToBase(Base base) {
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

    // findSurroundingCreepTiles uses breadth first search to find all creepTiles around a base.
    private HashSet<TilePosition> findSurroundingCreepTiles(Base base) {
        HashSet<TilePosition> creepTiles = new HashSet<>();
        HashSet<TilePosition> checked = new HashSet<>();
        Queue<TilePosition> candidates = new LinkedList<>();
        candidates.add(base.getLocation());

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

        return creepTiles;
    }

    /**
     * Pick a TilePosition to place a new creep colony (for later morphing into a Sunken Colony),
     * built toward the base’s closest choke.  If there’s existing reserved structures, attempts to build adjacent.
     */
    public TilePosition getLocationForCreepColony(Base base) {
        Position chokeCenter = closestChokeToBase(base);
        HashSet<TilePosition> creepTiles = findSurroundingCreepTiles(base);
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
                    if (res.getApproxDistance(cand) <= 15) {
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

}
