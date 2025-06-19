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
import bwem.Geyser;
import bwem.Mineral;
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
        Set<TilePosition> creepTiles = this.findSurroundingCreepTiles(base);
        for (TilePosition tp: creepTiles) {
            game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.Brown);
        }
    }

    public void debugBaseChoke(Base base) {
        Set<TilePosition> creepTiles = this.findSurroundingCreepTiles(base);
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

    public void debugMineralBoundingBox(Base base) {
        HashSet<TilePosition> tiles = mineralBoundingBox(base);
        if (!tiles.isEmpty()) {
            for (TilePosition tp: tiles) {
                if (tp != null) {
                    game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.Blue);
                }
            }
        }
    }

    public void debugGeyserBoundingBox(Base base) {
        HashSet<TilePosition> tiles = geyserBoundingBox(base);
        if (!tiles.isEmpty()) {
            for (TilePosition tp: tiles) {
                if (tp != null) {
                    game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.Blue);
                }
            }
        }
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

    private HashSet<TilePosition> geyserBoundingBox(Base base) {
        TilePosition topLeft = null;
        TilePosition bottomRight = null;
        for (Geyser geyser : base.getGeysers()) {
            TilePosition geyserTopLeft = geyser.getTopLeft();
            TilePosition geyserBottomRight = geyser.getBottomRight();

            game.drawBoxMap(
                    geyserTopLeft.toPosition(),
                    geyserBottomRight.toPosition(),
                    Color.Green
            );

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

    private HashSet<TilePosition> mineralBoundingBox(Base base) {
        TilePosition topLeft = null;
        TilePosition bottomRight = null;
        for (Mineral mineral: base.getMinerals()) {
            TilePosition mineralTopLeft = mineral.getTopLeft();
            TilePosition mineralBottomRight = mineral.getBottomRight();
            game.drawBoxMap(mineralTopLeft.toPosition(), mineralTopLeft.add(new TilePosition(1,1)).toPosition(), Color.Cyan);
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

    // findSurroundingCreepTiles uses breadth first search to find all creepTiles around a base.
    private Set<TilePosition> findSurroundingCreepTiles(Base base) {
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
     * built toward the base’s closest choke.  If there’s existing reserved structures, attempts to build adjacent.
     */
    public TilePosition getLocationForCreepColony(Base base) {
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

}
