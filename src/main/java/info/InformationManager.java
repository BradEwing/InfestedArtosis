package info;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.TilePosition;

import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;

import bwem.Tile;
import lombok.Data;

import map.TileComparator;
import map.TileInfo;
import map.TileType;
import state.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;


@Data
public class InformationManager {
    private BWEM bwem;
    private Game game;

    private GameState gameState;

    private HashSet<TilePosition> scoutTargets = new HashSet<>(); // TODO: Better data type to store and track this data
    private HashSet<TilePosition> activeScoutTargets = new HashSet<>();

    private ArrayList<TileInfo> scoutHeatMap = new ArrayList<>();

    private HashSet<Base> startingBasesSet = new HashSet<>();
    private HashSet<Base> expansionBasesSet = new HashSet<>();

    private HashSet<TilePosition> enemyBuildingPositions = new HashSet<>();
    private HashSet<Unit> enemyBuildings = new HashSet<>();
    private HashSet<Unit> enemyUnits = new HashSet<>();

    private Base mainEnemyBase;


    public InformationManager(BWEM bwem, Game game, GameState gameState) {
        this.bwem = bwem;
        this.game = game;
        this.gameState = gameState;

        initBases();
        initializeHeatMap();
    }

    public void onFrame() {
        ageHeatMap();
        //debugHeatMap();

        trackEnemyUnits();
        trackEnemyBuildings();
        // TODO: do same for unit positions?
        checkEnemyBuildingPositions();

        debugEnemyTargets();
        checkScoutTargets();
    }

    public void onUnitComplete(Unit unit) {
        return;
    }

    public void onUnitDestroy(Unit unit) {

        if (enemyBuildings.contains(unit)) {
            enemyBuildings.remove(unit);
        }

        if (enemyUnits.contains(unit)) {
            enemyUnits.remove(unit);
        }

        // TODO: move?
        if (unit.getType().isMineralField()) {
            removeMineral(unit);
        }

        if (unit.getType().isRefinery()) {
            removeGeyser(unit);
        }

        return;
    }

    // TODO(bug): I think problem is here or with calling functions
    // We keep flopping between states
    public boolean isEnemyLocationKnown() {
        return enemyUnits.size() + enemyBuildings.size() > 0;
    }

    private void debugEnemyLocations() {

        /*
        System.out.printf("Enemy Units: [");
        for (Unit enemyUnit: enemyUnits) {
            System.out.printf("%s, ", enemyUnit.getType());
        }
        System.out.printf("]\n");

        System.out.printf("Enemy Buildings: [");
        for (Unit building: enemyBuildings) {
            System.out.printf("%s, ", building.getType());
        }
        System.out.printf("]\n");

         */
    }

    public void setActiveScoutTarget(TilePosition target) {
        ensureScoutTargets();

        // TODO(bug): Sort
        // The heat map is sorted every frame, but we grab several items and put them into scoutTargets
        scoutTargets.remove(target);
        activeScoutTargets.add(target);
    }

    public TilePosition pollScoutTarget() {
        // Walk through
        if (mainEnemyBase == null) {
            Base baseTarget = fetchRandomBase(startingBasesSet);
            if (baseTarget != null) {
                return baseTarget.getLocation();
            }
        }

        HashSet<TilePosition> enemyBuildingPositions = getEnemyBuildingPositions();
        if (enemyBuildingPositions.size() > 0) {
            for (TilePosition target: enemyBuildingPositions) {
                if (!getScoutTargets().contains(target)) {
                    return target;
                }
            }
        }
        HashSet<TilePosition> scoutTargets = getScoutTargets();

        if (scoutHeatMap.size() > 0) {
            TileInfo scoutTile = scoutHeatMap.get(0);
            scoutTile.setImportance(0);
            return scoutTile.getTile();
        }

        for (TilePosition target: scoutTargets) {
            if (!getActiveScoutTargets().contains(target)) {
                return target;
            }
        }

        return null;
    }

    private void trackEnemyUnits() {
        for (Unit unit: game.getAllUnits()) {
            UnitType unitType = unit.getType();

            // TODO: this is pulling in bad data and polluting the lings
            // TODO: Track destructible, neutral units elsewhere
            // TODO: Destructable doors?! Disable Fortress
            if (unitType == UnitType.Special_Power_Generator || unitType == UnitType.Zerg_Larva || unitType == UnitType.Special_Pit_Door) {
                continue;
            }
            if (!unitType.isBuilding() && unit.getPlayer() != game.self() && !unitType.isResourceContainer() && !unitType.isNeutral() && !unit.isMorphing()) {
                enemyUnits.add(unit);
            }
        }

        List<Unit> unknownUnits = new ArrayList<>();
        for (Unit unit: enemyUnits) {
            if (!unit.isVisible()) {
                unknownUnits.add(unit);
            }
        }


        // Remove units we've lost sight of
        if (unknownUnits.size() > 1) {
            unknownUnits.stream().forEach(enemyUnits::remove);
        }
    }

    // TODO: Round robin
    private Base fetchRandomBase(HashSet<Base> basesSet) {
        return basesSet.stream().skip(new Random().nextInt(basesSet.size())).findFirst().orElse(null);
    }

    // TODO: Refactor into util
    private Base closestBaseToUnit(Unit unit, List<Base> baseList) {
        if (baseList.size() == 1) {
            return baseList.get(0);
        }
        Base closestBase = null;
        int closestDistance = Integer.MAX_VALUE;
        for (Base b : baseList) {
            int distance = unit.getDistance(b.getCenter());
            if (distance < closestDistance) {
                closestBase = b;
                closestDistance = distance;
            }
        }

        return closestBase;
    }

    private void trackEnemyBuildings() {
        for (Unit unit: game.getAllUnits()) {
            if (unit.getPlayer() == game.self()) {
                continue;
            }
            UnitType unitType = unit.getType();



            // Unsure if there are repercussions here, but skipping units where type is unknown
            // TODO: track neutral buildings elsewhere
            if (unitType == UnitType.Unknown || unitType == UnitType.Special_Power_Generator ||
                    unitType == UnitType.Special_Protoss_Temple || unitType == UnitType.Special_XelNaga_Temple ||
                unitType == UnitType.Special_Psi_Disrupter) {
                continue;
            }

            if (unit.isVisible() && unitType.isBuilding() && (!unitType.isResourceContainer() || unitType.isRefinery()) && !unitType.isNeutral()) {
                //System.out.printf("Enemy Building Found, Type: [%s] Player: [%s]\n", unit.getType(), unit.getPlayer().toString());
                enemyBuildings.add(unit);
                enemyBuildingPositions.add(unit.getTilePosition());

                // If enemyBase is unknown and this is our first time encountering an enemyUnit, set enemyBase
                if (mainEnemyBase == null && unitType.isResourceContainer()) {
                    mainEnemyBase = closestBaseToUnit(unit, startingBasesSet.stream().collect(Collectors.toList()));
                }
            }
        }

    }

    private void removeMineral(Unit mineral) {
        gameState.getMineralAssignments().remove(mineral);
    }

    private void removeGeyser(Unit geyser) {
        gameState.getGeyserAssignments().remove(geyser);
    }

    private boolean canSeeEnemyBuilding() {
        for (Unit building: enemyBuildings) {
            // BWAPI sets type to unknown when the current player doesn't have visibility
            if (building.getType() != UnitType.Unknown) {
                return true;
            }
        }
        return false;
    }

    // TODO: Only check this if we see new enemy buildings
    private void checkEnemyBuildingPositions() {
        if (canSeeEnemyBuilding()) {
            return;
        }
        List<TilePosition> foundBuildings = new ArrayList<>();
        for (TilePosition tilePosition: enemyBuildingPositions) {
            if (game.isVisible(tilePosition)) {
                foundBuildings.add(tilePosition);
            }
        }

        foundBuildings.stream().forEach(buildingPosition -> enemyBuildingPositions.remove(buildingPosition));
        return;
    }

    private void ensureScoutTargets() {
        if (scoutTargets.size() < 1) {
            assignNewScoutTargets();
        }
    }

    private void checkScoutTargets() {
        ensureScoutTargets();

        // Avoid ConcurrentModificationException
        List<TilePosition> foundTargets = new ArrayList<>();
        // Are we possibly sending multiple units to the same scout target?
        // And if we remove it here, then the other scout encounters a null target
        for (TilePosition target: activeScoutTargets) {
            // Bug: null targets are getting passed in! gracefully recover, but log error to out
            if (target == null) {
                // TODO: Logging
                //System.out.printf(String.format("[WARN] target is null! activeScoutTargets: [%s]\n", activeScoutTargets));
                activeScoutTargets.remove(null);
                return;
            }
            if (game.isVisible(target)) {
                foundTargets.add(target);
            }
        }
        for (TilePosition target: foundTargets) {
            activeScoutTargets.remove(target);
            // TODO: EXPERIMENTAL, trying to get rid of orange dots persisting
            enemyBuildingPositions.remove(target);
        }

        if (game.getFrameCount() % 100 == 0) {
            //System.out.printf("checkScoutTargets(), scoutTargets: [%s], activeScoutTargets: [%s]\n", scoutTargets, activeScoutTargets);
        }
    }

    // Iterate through heat map and assign all tiles
    private void assignNewScoutTargets() {
        // Round robin assign SCOUTs to bases
        int curImportance = 0;
        for (TileInfo tileInfo : scoutHeatMap) {
            TilePosition tile = tileInfo.getTile();
            int importance = tileInfo.getImportance();
            if (curImportance == 0) {
                curImportance = importance;
            }
            // Only add highest importance tiles, then break
            // We add in the event of ties
            if (importance < curImportance) {
                break;
            }
            if (!scoutTargets.contains(tile) && tileInfo.isWalkable()) {
                scoutTargets.add(tile);
            }
        }
    }

    private void initBases() {
        for (final Base b : bwem.getMap().getBases()) {
            TilePosition tilePosition = b.getLocation();
            if (b.isStartingLocation()) {
                startingBasesSet.add(b);
                scoutTargets.add(tilePosition);
            } else {
                expansionBasesSet.add(b);
            }
        }
    }

    private void initializeHeatMap() {
        HashSet<TilePosition> startingPositions = new HashSet<>();
        HashSet<TilePosition> expansionPositions = new HashSet<>();

        // Convert sets of Bases into sets of TilePosition
        startingBasesSet.stream().map(base -> base.getLocation()).forEach(startingPositions::add);
        expansionBasesSet.stream().map(base -> base.getLocation()).forEach(expansionPositions::add);

        for (int i = 0; i< game.mapWidth(); i++) {
            for (int j = 0; j < game.mapHeight(); j++) {
                TilePosition tp = new TilePosition(i,j);
                TileInfo tileInfo;
                if (startingPositions.contains(tp)) {
                    tileInfo = new TileInfo(tp, 2, game.isWalkable(tp.toWalkPosition()), TileType.BASE_START);
                } else if (expansionPositions.contains(tp)) {
                    tileInfo = new TileInfo(tp, 1, game.isWalkable(tp.toWalkPosition()), TileType.BASE_EXPANSION);
                } else {
                    tileInfo = new TileInfo(tp, 0, game.isWalkable(tp.toWalkPosition()), TileType.NORMAL);
                }
                scoutHeatMap.add(tileInfo);
            }
        }
    }

    private void ageHeatMap() {
        int weight = 1;
        for (TileInfo tileInfo : scoutHeatMap) {
            if (game.isVisible(tileInfo.getTile())) {
                tileInfo.setImportance(0);
                scoutTargets.remove(tileInfo.getTile());
            } else {
                if (tileInfo.getType() == TileType.BASE_START) {
                    weight = 3;
                } else if (tileInfo.getType() == TileType.BASE_EXPANSION) {
                    weight = 2;
                } else if (tileInfo.getType() == TileType.NORMAL) {
                    weight = 1;
                }
                tileInfo.setImportance(tileInfo.getImportance()+weight);
            }

        }
        // TODO: sorting on EVERY frame sounds like a potential cpu nightmare
        Collections.sort(scoutHeatMap, new TileComparator());
    }

    private void debugHeatMap() {
        for (TileInfo tileInfo : scoutHeatMap) {
            game.drawTextMap(
                    (tileInfo.getTile().getX() * 32) + 8,
                    (tileInfo.getTile().getY() * 32) + 8,
                    String.valueOf(tileInfo.getImportance()),
                    Text.White);
        }
    }

    private void debugEnemyTargets() {
        if (game.getFrameCount() % 100 == 0) {
            debugEnemyLocations();
            //System.out.printf("Enemy base: [%s]\n", mainEnemyBase);
        }
        for (Unit target: enemyBuildings) {
            game.drawCircleMap(target.getPosition(), 3, Color.Red);
        }
        for (Unit target: enemyUnits) {
            game.drawCircleMap(target.getPosition(), 3, Color.Red);
        }
        for (TilePosition tilePosition: enemyBuildingPositions) {
            game.drawCircleMap(tilePosition.toPosition(), 2, Color.Orange);
        }
    }
}
