package info;

import bwapi.Color;
import bwapi.Game;
import bwapi.Text;
import bwapi.TilePosition;

import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;

import map.TileComparator;
import map.TileInfo;
import map.TileType;
import planner.PlanType;
import planner.PlannedItem;
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

    private HashSet<TilePosition> scoutTargets = new HashSet<>(); // TODO: Better data type to store and track this data
    private HashSet<TilePosition> activeScoutTargets = new HashSet<>();

    private ArrayList<TileInfo> scoutHeatMap = new ArrayList<>();

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
        }
    }

    public void onUnitShow(Unit unit) {
        // TODO: Clean up
        UnitType unitType = unit.getInitialType();

        if (unit.getPlayer() == game.self()) {
            updateTechProgression(unitType);
            UnitTypeCount unitCount = gameState.getUnitTypeCount();
            unitCount.addUnit(unitType);
            unitCount.unplanUnit(unitType);
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

        HashMap<Unit, PlannedItem> assignedPlannedItems = gameState.getAssignedPlannedItems();
        PlannedItem assignedPlan = assignedPlannedItems.get(unit);
        // Currently, only do something here if this unit is assigned to a plan.
        if (assignedPlan == null) {
            return;
        }

        UnitType plannedUnit = assignedPlan.getPlannedUnit();
        if (assignedPlan.getType() == PlanType.BUILDING) {
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
            updateTechOnDestroy(unitType);

            UnitTypeCount unitCount = gameState.getUnitTypeCount();
            unitCount.removeUnit(unitType);
        }

        // TODO: move?
        if (unitType.isMineralField()) {
            removeMineral(unit);
        }

        if (unitType.isRefinery()) {
            removeGeyser(unit);
        }

        return;
    }

    public boolean isEnemyLocationKnown() {
        return visibleEnemyUnits.size() + enemyBuildings.size() > 0;
    }

    public boolean isEnemyBuildingLocationKnown() {
        return enemyBuildingPositions.size() > 0;
    }

    public boolean isEnemyUnitVisible() {
        for (Unit enemy: visibleEnemyUnits) {
            if (enemy.isVisible()) {
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
        // The heat map is sorted every frame, but we grab several items and put them into scoutTargets
        scoutTargets.remove(target);
        activeScoutTargets.add(target);
    }

    public TilePosition pollScoutTarget(boolean allowDuplicateScoutTarget) {
        HashSet<TilePosition> enemyBuildingPositions = getEnemyBuildingPositions();
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
                if (!getScoutTargets().contains(target) || allowDuplicateScoutTarget) {
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
