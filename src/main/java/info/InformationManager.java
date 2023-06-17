package info;

import bwapi.Color;
import bwapi.Game;
import bwapi.Text;
import bwapi.TilePosition;

import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwem.BWEM;
import bwem.Base;

import bwem.Geyser;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileScoutImportanceComparator;
import info.map.MapTileType;
import planner.Plan;
import planner.PlanType;
import strategy.strategies.UnitWeights;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class InformationManager {
    private BWEM bwem;
    private Game game;

    private GameState gameState;

    private BaseManager baseManager;

    private HashSet<TilePosition> scoutTargets = new HashSet<>(); // TODO: Better data type to store and track this data
    private HashSet<TilePosition> activeScoutTargets = new HashSet<>();

    // TODO: Move to GameState
    private HashSet<Base> startingBasesSet = new HashSet<>();
    private HashSet<Base> expansionBasesSet = new HashSet<>();
    private HashSet<TilePosition> startingBasesTilePositions = new HashSet<>();

    private HashSet<TilePosition> enemyBuildingPositions = new HashSet<>();
    private HashSet<Unit> enemyBuildings = new HashSet<>();
    private HashSet<Unit> visibleEnemyUnits = new HashSet<>();
    private HashSet<Unit> enemyUnits = new HashSet<>();

    private HashMap<Unit, TilePosition> enemyLastKnownLocations = new HashMap<>();

    private Base myBase;
    private Base mainEnemyBase;

    private HashMap<Base, Integer> baseScoutAssignments = new HashMap<>();
    private HashMap<TilePosition, Base> tilePositionToBaseLookup = new HashMap<>();


    public InformationManager(BWEM bwem, Game game, GameState gameState) {
        this.bwem = bwem;
        this.game = game;
        this.gameState = gameState;

        initBases();
        initializeGameMap();

        this.baseManager = new BaseManager(bwem, game, gameState);
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

        checkIfEnemyUnitsStillThreatenBase();
        checkBaseThreats();

        debugInitialHatch();
    }

    public void onUnitHide(Unit unit) {
        // TODO: Clean up
        UnitType unitType = unit.getInitialType();
        if (unit.getPlayer() == game.self() ||
                unitType == UnitType.Resource_Mineral_Field ||
                unitType == UnitType.Resource_Vespene_Geyser ||
                unitType == UnitType.Powerup_Mineral_Cluster_Type_1 ||
                unitType == UnitType.Powerup_Mineral_Cluster_Type_2 ||
                unitType == UnitType.Resource_Mineral_Field_Type_2 ||
                unitType == UnitType.Resource_Mineral_Field_Type_3) {
            return;
        }


        if (unitType == UnitType.Unknown || unitType == UnitType.Special_Power_Generator ||
                unitType == UnitType.Special_Protoss_Temple || unitType == UnitType.Special_XelNaga_Temple ||
                unitType == UnitType.Special_Psi_Disrupter) {
            return;
        }

        if (unitType == UnitType.Zerg_Larva) {
            return;
        }

        enemyLastKnownLocations.put(unit, unit.getTilePosition());
    }

    /**
     * Update GameState's tech progression to enable new units, tech or upgrades.
     *
     * @param unitType tech building
     */
    public void updateTechProgression(UnitType unitType) {
        TechProgression techProgression = gameState.getTechProgression();
        UnitWeights unitWeights = gameState.getUnitWeights();

        switch(unitType) {
            case Zerg_Spawning_Pool:
                techProgression.setSpawningPool(true);
                unitWeights.enableUnit(UnitType.Zerg_Zergling);
                break;
            case Zerg_Hydralisk_Den:
                techProgression.setHydraliskDen(true);
                unitWeights.enableUnit(UnitType.Zerg_Hydralisk);
                break;
            case Zerg_Spire:
                techProgression.setSpire(true);
                unitWeights.enableUnit(UnitType.Zerg_Mutalisk);
                unitWeights.enableUnit(UnitType.Zerg_Scourge);
                break;
            case Zerg_Lair:
                techProgression.setLair(true);
                break;
            case Zerg_Evolution_Chamber:
                techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers()-1);
                techProgression.setEvolutionChambers(techProgression.evolutionChambers()+1);
                break;
            case Zerg_Queens_Nest:
                techProgression.setQueensNest(true);
                unitWeights.enableUnit(UnitType.Zerg_Queen);
                break;
            case Zerg_Hive:
                techProgression.setHive(true);
                break;
        }
    }

    public void onUnitShow(Unit unit) {
        // TODO: Clean up
        UnitType unitType = unit.getInitialType();

        if (unit.getPlayer() == game.self()) {
            updateTechProgression(unitType);
            return;
        }
        if (unitType == UnitType.Resource_Mineral_Field ||
                unitType == UnitType.Resource_Vespene_Geyser ||
                unitType == UnitType.Powerup_Mineral_Cluster_Type_1 ||
                unitType == UnitType.Powerup_Mineral_Cluster_Type_2 ||
                unitType == UnitType.Resource_Mineral_Field_Type_2 ||
                unitType == UnitType.Resource_Mineral_Field_Type_3) {
            return;
        }


        if (unitType == UnitType.Unknown || unitType == UnitType.Special_Power_Generator ||
                unitType == UnitType.Special_Protoss_Temple || unitType == UnitType.Special_XelNaga_Temple ||
                unitType == UnitType.Special_Psi_Disrupter) {
            return;
        }

        if (unitType == UnitType.Zerg_Larva) {
            return;
        }

        if (enemyLastKnownLocations.containsKey(unit)) {
            enemyLastKnownLocations.remove(unit);
        }
    }

    public void onUnitMorph(Unit unit) {
        UnitType unitType = unit.getInitialType();

        HashMap<Unit, Plan> assignedPlannedItems = gameState.getAssignedPlannedItems();
        Plan assignedPlan = assignedPlannedItems.get(unit);
        // Currently, only do something here if this unit is assigned to a plan.
        if (assignedPlan == null) {
            return;
        }

        UnitType plannedUnit = assignedPlan.getPlannedUnit();
        if (assignedPlan.getType() == PlanType.BUILDING) {
            UnitTypeCount count = gameState.getUnitTypeCount();
            count.removeUnit(UnitType.Zerg_Drone);
            updateTechProgression(plannedUnit);
        }
    }

    public void onUnitComplete(Unit unit) {
        UnitType unitType = unit.getType();
        UnitTypeCount unitCount = gameState.getUnitTypeCount();
        unitCount.addUnit(unitType);
        unitCount.unplanUnit(unitType);
        return;
    }

    public void onUnitRenegade(Unit unit) {
        if (unit.getType() == UnitType.Resource_Vespene_Geyser) {
            onUnitDestroy(unit);
        }
    }

    /**
     * Update GameState's tech progression to prevent strategy and production from considering given tech.
     *
     * @param unitType tech building
     */
    public void updateTechOnDestroy(UnitType unitType) {
        TechProgression techProgression = gameState.getTechProgression();
        UnitWeights unitWeights = gameState.getUnitWeights();

        switch(unitType) {
            case Zerg_Spawning_Pool:
                techProgression.setSpawningPool(false);
                unitWeights.disableUnit(UnitType.Zerg_Zergling);
                break;
            case Zerg_Hydralisk_Den:
                techProgression.setHydraliskDen(false);
                unitWeights.disableUnit(UnitType.Zerg_Hydralisk);
                break;
            case Zerg_Spire:
                techProgression.setSpire(false);
                unitWeights.disableUnit(UnitType.Zerg_Mutalisk);
                unitWeights.disableUnit(UnitType.Zerg_Scourge);
                break;
            case Zerg_Lair:
                techProgression.setLair(false);
                break;
            case Zerg_Evolution_Chamber:
                final int evolutionChambers = techProgression.getEvolutionChambers();
                techProgression.setEvolutionChambers(evolutionChambers-1);
                break;
            case Zerg_Queens_Nest:
                techProgression.setQueensNest(false);
                unitWeights.disableUnit(UnitType.Zerg_Queen);
                break;
            case Zerg_Hive:
                techProgression.setHive(false);
                break;
        }
    }

    public void onUnitDestroy(Unit unit) {
        UnitType unitType = unit.getType();

        if (enemyBuildings.contains(unit)) {
            enemyBuildings.remove(unit);
        }

        if (visibleEnemyUnits.contains(unit)) {
            visibleEnemyUnits.remove(unit);
        }

        if (enemyLastKnownLocations.containsKey(unit)) {
            enemyLastKnownLocations.remove(unit);
        }

        // TODO: filter friendly units?
        ensureEnemyUnitRemovedFromBaseThreats(unit);

        if (unit.getPlayer() == game.self()) {
            baseManager.onUnitDestroy(unit);
            updateTechOnDestroy(unitType);
            UnitTypeCount unitCount = gameState.getUnitTypeCount();
            unitCount.removeUnit(unitType);
        }
    }

    public boolean isEnemyLocationKnown() {
        return visibleEnemyUnits.size() + enemyBuildings.size() > 0;
    }

    public boolean isEnemyBuildingLocationKnown() {
        return enemyBuildingPositions.size() > 0;
    }

    public boolean isEnemyUnitVisible() {
        for (Unit enemy: visibleEnemyUnits) {
            if (enemy.isDetected()) {
                return true;
            }
        }
        for (Unit enemy: enemyBuildings) {
            if (enemy.isVisible()) {
                return true;
            }
        }

        return false;
    }

    public void setActiveScoutTarget(TilePosition target) {
        ensureScoutTargets();

        // TODO(bug): Sort
        // The heat info.map is sorted every frame, but we grab several items and put them into scoutTargets
        scoutTargets.remove(target);
        activeScoutTargets.add(target);
    }

    public TilePosition pollScoutTarget(boolean allowDuplicateScoutTarget) {
        // Walk through
        if (mainEnemyBase == null && enemyBuildingPositions.size() == 0) {
            Base baseTarget = fetchBaseRoundRobin(baseScoutAssignments.keySet());
            if (baseTarget != null) {
                Integer assignments = baseScoutAssignments.get(baseTarget);
                baseScoutAssignments.put(baseTarget, assignments+1);
                return baseTarget.getLocation();
            }
        }


        if (enemyBuildingPositions.size() > 0) {
            for (TilePosition target: enemyBuildingPositions) {
                if (!scoutTargets.contains(target) || allowDuplicateScoutTarget) {
                    return target;
                }
            }
        }

        ArrayList<MapTile> heatMap = gameState.getGameMap().getHeatMap();

        GameMap gameMap = gameState.getGameMap();
        if (gameMap.getHeatMap().size() > 0) {
            MapTile scoutTile = gameMap.getHeatMap().get(0);
            scoutTile.setScoutImportance(0);
            return scoutTile.getTile();
        }

        for (TilePosition target: scoutTargets) {
            if (!activeScoutTargets.contains(target)) {
                return target;
            }
        }

        return null;
    }

    public HashSet<Unit> getEnemyBuildings() {
        return enemyBuildings;
    }

    public HashSet<Unit> getVisibleEnemyUnits() {
        return visibleEnemyUnits;
    }

    public HashSet<TilePosition> getActiveScoutTargets() {
        return activeScoutTargets;
    }

    // TODO: Remove in favour of onUnitShow/onUnitHide hooks
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
                visibleEnemyUnits.add(unit);
            }
        }

        List<Unit> unknownUnits = new ArrayList<>();
        for (Unit unit: visibleEnemyUnits) {
            if (!unit.isVisible()) {
                unknownUnits.add(unit);
            }
        }


        // Remove units we've lost sight of
        if (unknownUnits.size() > 1) {
            unknownUnits.stream().forEach(visibleEnemyUnits::remove);
        }

        List<Unit> enemyUnitsNotAtLastKnownLocation = new ArrayList<>();
        for (Unit unit: enemyLastKnownLocations.keySet()) {
            TilePosition tp = enemyLastKnownLocations.get(unit);
            if (game.isVisible(tp)) {
                enemyUnitsNotAtLastKnownLocation.add(unit);
            }
        }

        for (Unit unit: enemyUnitsNotAtLastKnownLocation) {
            enemyLastKnownLocations.remove(unit);
        }
    }

    private Base fetchBaseRoundRobin(Set<Base> candidateBases) {
        Base leastScoutedBase = null;
        Integer fewestScouts = Integer.MAX_VALUE;
        for (Base base: candidateBases) {
            Integer assignedScoutsToBase = baseScoutAssignments.get(base);
            if (assignedScoutsToBase < fewestScouts) {
                leastScoutedBase = base;
                fewestScouts = assignedScoutsToBase;
            }

        }
        return leastScoutedBase;
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
            if (unit.getPlayer() != game.enemy()) {
                continue;
            }
            UnitType unitType = unit.getType();


            if (unit.isVisible() && unitType.isBuilding()) {
                enemyBuildings.add(unit);
                enemyBuildingPositions.add(unit.getTilePosition());

                // If enemyBase is unknown and this is our first time encountering an enemyUnit, set enemyBase
                if (mainEnemyBase == null && unitType.isResourceDepot()) {
                    mainEnemyBase = closestBaseToUnit(unit, startingBasesSet.stream().collect(Collectors.toList()));
                }
            }
        }

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

        for (TilePosition target: startingBasesTilePositions) {
            if (game.isVisible(target)) {
                foundTargets.add(target);
            }
        }

        for (TilePosition target: foundTargets) {
            activeScoutTargets.remove(target);
            enemyBuildingPositions.remove(target);
            startingBasesTilePositions.remove(target);
            if (tilePositionToBaseLookup.containsKey(target)) {
                Base b = tilePositionToBaseLookup.get(target);
                baseScoutAssignments.remove(b, target);
                tilePositionToBaseLookup.remove(target);
            }
        }

        if (game.getFrameCount() % 100 == 0) {
            //System.out.printf("checkScoutTargets(), scoutTargets: [%s], activeScoutTargets: [%s]\n", scoutTargets, activeScoutTargets);
        }


    }

    // Iterate through heat info.map and assign all tiles
    private void assignNewScoutTargets() {
        // Round robin assign SCOUTs to bases
        int curImportance = 0;
        for (MapTile mapTile : gameState.getGameMap().getHeatMap()) {
            TilePosition tile = mapTile.getTile();
            int importance = mapTile.getScoutImportance();
            if (curImportance == 0) {
                curImportance = importance;
            }
            // Only add highest importance tiles, then break
            // We add in the event of ties
            if (importance < curImportance) {
                break;
            }
            if (!scoutTargets.contains(tile) && mapTile.isBuildable()) {
                scoutTargets.add(tile);
            }
        }
    }

    private void initBases() {
        TilePosition initialHatchery = null;

        for (Unit unit: game.getAllUnits()) {
            if (unit.getPlayer() != game.self()) {
                continue;
            }

            if (unit.getType() == UnitType.Zerg_Hatchery) {
                initialHatchery = unit.getTilePosition();
                break;
            }
        }
        for (final Base b : bwem.getMap().getBases()) {
            TilePosition tilePosition = b.getLocation();
            if (b.isStartingLocation()) {
                if (tilePosition.getX() == initialHatchery.getX() && tilePosition.getY() == initialHatchery.getY()) {
                    myBase = b;
                }
                startingBasesTilePositions.add(tilePosition);
                tilePositionToBaseLookup.put(tilePosition, b);
                baseScoutAssignments.put(b, 0);
                startingBasesSet.add(b);
                scoutTargets.add(tilePosition);
            } else {
                expansionBasesSet.add(b);
            }
        }
    }

    private void initializeGameMap() {
        HashSet<TilePosition> startingPositions = new HashSet<>();
        HashSet<TilePosition> expansionPositions = new HashSet<>();
        HashSet<TilePosition> resourcePositions = new HashSet<>();

        // Convert sets of Bases into sets of TilePosition
        startingBasesSet.stream().map(base -> base.getLocation()).forEach(startingPositions::add);
        expansionBasesSet.stream().map(base -> base.getLocation()).forEach(expansionPositions::add);

        // TODO: Add resource locations
        // TODO: THIS IS BROKEN
        for (Base base: startingBasesSet) {
            for (Geyser geyser: base.getGeysers()) {
                TilePosition topLeft = geyser.getTopLeft();
                TilePosition bottomRight = geyser.getBottomRight();
                for (int x = topLeft.getX(); x < bottomRight.getX(); x++) {
                    for (int y = topLeft.getY(); y < bottomRight.getY(); y++) {
                        resourcePositions.add(topLeft.add(new TilePosition(x, y)));
                    }
                }
            }
        }

        GameMap gameMap = new GameMap(game.mapWidth(), game.mapHeight());

        for (int x = 0; x < game.mapWidth(); x++) {
            for (int y = 0; y < game.mapHeight(); y++) {
                TilePosition tp = new TilePosition(x,y);
                MapTile mapTile;
                if (startingPositions.contains(tp)) {
                    mapTile = new MapTile(tp, 2, true, MapTileType.BASE_START);
                } else if (expansionPositions.contains(tp)) {
                    mapTile = new MapTile(tp, 1, true, MapTileType.BASE_EXPANSION);
                } else if (resourcePositions.contains(tp)) {
                    mapTile = new MapTile(tp, 0, false, MapTileType.NORMAL);
                } else {
                    mapTile = new MapTile(tp, 0, this.isBuildable(tp), MapTileType.NORMAL);
                }
                gameMap.addTile(mapTile, x, y);
            }
        }

        gameState.setGameMap(gameMap);
    }

    // Iterates over all walk positions in a tile
    private boolean isBuildable(TilePosition tp) {
        WalkPosition wp = tp.toWalkPosition();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                if (!game.isWalkable(wp.add(new WalkPosition(x, y)))) {
                    return false;
                }
            }
        }

        return true;
    }

    // TODO: Move to GameMap?
    private void ageHeatMap() {
        int weight = 1;
        GameMap gameMap = gameState.getGameMap();
        for (MapTile mapTile : gameMap.getHeatMap()) {
            if (game.isVisible(mapTile.getTile())) {
                mapTile.setScoutImportance(0);
                scoutTargets.remove(mapTile.getTile());
            } else {
                if (mapTile.getType() == MapTileType.BASE_START) {
                    weight = 3;
                } else if (mapTile.getType() == MapTileType.BASE_EXPANSION) {
                    weight = 2;
                } else if (mapTile.getType() == MapTileType.NORMAL) {
                    weight = 1;
                }
                mapTile.setScoutImportance(mapTile.getScoutImportance()+weight);
            }

        }
        // TODO: sorting on EVERY frame sounds like a potential cpu nightmare
        Collections.sort(gameMap.getHeatMap(), new MapTileScoutImportanceComparator());
    }

    private void debugInitialHatch() {
        if (game.getFrameCount() < 10) {
            return;
        }
        game.drawBoxMap(myBase.getLocation().toPosition(), myBase.getLocation().add(new TilePosition(1,1)).toPosition(), Color.Blue);
    }

    private void debugEnemyTargets() {
        for (Unit target: enemyBuildings) {
            game.drawCircleMap(target.getPosition(), 3, Color.Yellow);
        }
        for (Unit target: visibleEnemyUnits) {
            game.drawCircleMap(target.getPosition(), 3, Color.Red);
        }
        for (TilePosition tilePosition: enemyBuildingPositions) {
            game.drawCircleMap(tilePosition.toPosition(), 2, Color.Orange);
        }

        for (Unit unit: enemyLastKnownLocations.keySet()) {
            TilePosition tp = enemyLastKnownLocations.get(unit);
            game.drawTextMap(tp.toPosition(), String.format("%s", unit.getInitialType()), Text.White);
        }
    }

    private void ensureEnemyUnitRemovedFromBaseThreats(Unit unit) {
        for (HashSet<Unit> baseThreat: gameState.getBaseToThreatLookup().values()) {
            if (baseThreat.contains(unit)) {
                baseThreat.remove(unit);
            }
        }
    }

    private void checkIfEnemyUnitsStillThreatenBase() {
        HashMap<Base, HashSet<Unit>> baseThreats = gameState.getBaseToThreatLookup();
        for (Base base: baseThreats.keySet()) {
            HashSet<Unit> unitThreats = baseThreats.get(base);
            List<Unit> noLongerThreats = new ArrayList<>();
            for (Unit unit: unitThreats) {
                if (unit.getType() == UnitType.Unknown) {
                    noLongerThreats.add(unit);
                    continue;
                }
                int distance = (int) base.getLocation().toPosition().getDistance(unit.getTilePosition().toPosition());
                if (distance > 256) {
                    noLongerThreats.add(unit);
                    continue;
                }
                if (unit.getPlayer() == game.self()) {
                    noLongerThreats.add(unit);
                    continue;
                }
            }

            for (Unit unit: noLongerThreats) {
                unitThreats.remove(unit);
            }
        }
    }

    private void checkBaseThreats() {
        if (visibleEnemyUnits.size() < 1) {
            return;
        }

        Set<Base> bases = gameState.getGatherersAssignedToBase().keySet();
        HashMap<Base, HashSet<Unit>> baseThreats = gameState.getBaseToThreatLookup();
        for (Base base: bases) {
            if (!baseThreats.containsKey(base)) {
                baseThreats.put(base, new HashSet<>());
            }

            for (Unit unit: visibleEnemyUnits) {
                if (base.getLocation().toPosition().getDistance(unit.getTilePosition().toPosition()) < 256) {
                    baseThreats.get(base).add(unit);
                }
            }
        }
    }
}
