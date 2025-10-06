package info;

import bwapi.Color;
import bwapi.Game;
import bwapi.PlayerType;
import bwapi.Position;
import bwapi.Race;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwem.BWEM;
import bwem.Base;
import bwem.Geyser;
import info.map.BuildingPlanner;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileType;
import info.tracking.ObservedUnitTracker;
import lombok.Getter;
import macro.plan.Plan;
import macro.plan.PlanType;
import org.jetbrains.annotations.Nullable;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static util.Filter.isHostileBuildingToGround;


public class InformationManager {
    private BWEM bwem;
    private Game game;

    private GameState gameState;

    private BaseManager baseManager;

    // TODO: Move to GameState
    private HashSet<Base> startingBasesSet = new HashSet<>();
    private HashSet<Base> expansionBasesSet = new HashSet<>();
    private HashSet<TilePosition> startingBasesTilePositions = new HashSet<>();

    @Getter
    private HashSet<Unit> enemyBuildings = new HashSet<>();
    private HashSet<Unit> enemyHostileToGroundBuildings = new HashSet<>();
    @Getter
    private HashSet<Unit> visibleEnemyUnits = new HashSet<>();

    private HashMap<Unit, TilePosition> enemyLastKnownLocations = new HashMap<>();
    private HashMap<TilePosition, Base> tilePositionToBaseLookup = new HashMap<>();

    private static final int DETECTION_DISTANCE = 10;


    public InformationManager(BWEM bwem, Game game, GameState gameState) {
        this.bwem = bwem;
        this.game = game;
        this.gameState = gameState;

        initBases();
        initializeGameMap();

        this.baseManager = new BaseManager(bwem, game, gameState);
    }

    public void onFrame() {
        gameState.onFrame();
        ageHeatMap();

        trackEnemyUnits();
        trackEnemyBuildings();
        checkEnemyBuildingPositions();
        debugEnemyTargets();
        checkScoutTargets();
        checkIfEnemyUnitsStillThreatenBase();
        checkBaseThreats();

        BuildOrder active = gameState.getActiveBuildOrder();
        if (active.shouldTransition(gameState)) {
            BuildOrder transition = transitionBuildOrder();
            gameState.setActiveBuildOrder(transition);
            gameState.setTransitionBuildOrder(true);
        }
    }

    public void onUnitHide(Unit unit) {
        UnitType unitType = unit.getInitialType();

        // Check if the unit should be ignored: own unit, resource, powerup, special/unknown
        if (unit.getPlayer() == game.self()
                || unitType.isResourceContainer()
                || unitType.isMineralField()
                || unitType.isNeutral()
                || unitType.isSpecialBuilding()
                || unitType == UnitType.Unknown) {
            return;
        }

        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        tracker.onUnitHide(unit, game.getFrameCount());

        enemyLastKnownLocations.put(unit, unit.getTilePosition());
    }

    /**
     * Update GameState's tech progression to enable new units, tech or upgrades.
     *
     * @param unitType tech building
     */
    public void updateTechProgression(UnitType unitType) {
        TechProgression techProgression = gameState.getTechProgression();

        switch(unitType) {
            case Zerg_Spawning_Pool:
                techProgression.setSpawningPool(true);
                break;
            case Zerg_Hydralisk_Den:
                techProgression.setHydraliskDen(true);
                break;
            case Zerg_Spire:
                techProgression.setSpire(true);
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
                break;
            case Zerg_Hive:
                techProgression.setHive(true);
                break;
        }
    }

    public void onUnitShow(Unit unit) {
        UnitType unitType = unit.getInitialType();
        PlayerType playerType = unit.getPlayer().getType();

        if (playerType == PlayerType.Neutral || playerType == PlayerType.None) {
            return;
        }

        if (unit.getPlayer() == game.self()) {
            updateTechProgression(unitType);
            return;
        }
        // Check if the unit should be ignored: resource, powerup, special/unknown
        if (unitType.isResourceContainer()
                || unitType.isMineralField()
                || unitType.isNeutral()
                || unitType.isSpecialBuilding()
                || unitType == UnitType.Unknown) {
            return;
        }

        if (gameState.getOpponentRace() == Race.Unknown) {
            Race race = unit.getType().getRace();
            gameState.updateRace(race);
        }

        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        boolean isProxied = isProxiedBuilding(unit);
        tracker.onUnitShow(unit, game.getFrameCount(), isProxied);


        enemyLastKnownLocations.remove(unit);
    }

    public void onUnitMorph(Unit unit) {
        HashMap<Unit, Plan> assignedPlannedItems = gameState.getAssignedPlannedItems();
        Plan assignedPlan = assignedPlannedItems.get(unit);
        // Currently, only do something here if this unit is assigned to a plan.
        if (assignedPlan == null) {
            return;
        }

        UnitType plannedUnit = assignedPlan.getPlannedUnit();
        if (assignedPlan.getType() == PlanType.BUILDING) {
            UnitTypeCount count = gameState.getUnitTypeCount();
            // TODO: Spore Colony, Greater Spire
            if (plannedUnit == UnitType.Zerg_Sunken_Colony) {
                count.removeUnit(UnitType.Zerg_Creep_Colony);
            } else if (plannedUnit == UnitType.Zerg_Lair) {
                count.removeUnit(UnitType.Zerg_Hatchery);
            } else {
                count.removeUnit(UnitType.Zerg_Drone);
            }

            updateTechProgression(plannedUnit);
        }
    }

    public void onUnitComplete(Unit unit) {
        UnitType unitType = unit.getType();
        UnitTypeCount unitCount = gameState.getUnitTypeCount();
        unitCount.addUnit(unitType);
        unitCount.unplanUnit(unitType);
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

        switch(unitType) {
            case Zerg_Spawning_Pool:
                techProgression.setSpawningPool(false);
                break;
            case Zerg_Hydralisk_Den:
                techProgression.setHydraliskDen(false);
                break;
            case Zerg_Spire:
                techProgression.setSpire(false);
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

        if (enemyHostileToGroundBuildings.contains(unit)) {
            enemyHostileToGroundBuildings.remove(unit);
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
        } else {
            BaseData baseData = gameState.getBaseData();
            TilePosition tp = unit.getTilePosition();
            if (unitType.isResourceDepot() && baseData.isBaseTilePosition(tp)) {
                Base enemyBaseCandidate = baseData.baseAtTilePosition(tp);
                baseData.removeEnemyBase(enemyBaseCandidate);
            }
            ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
            tracker.onUnitDestroy(unit, game.getFrameCount());
        }
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

    public int getEnemyHostileToGroundBuildingsCount() {
        return enemyHostileToGroundBuildings.size();
    }

    /**
     * Default rally to main, then natural expansion
     *
     * @return TilePosition to rally units to
     */
    public Position getRallyPoint() {
        BaseData baseData = gameState.getBaseData();
        if (baseData.hasNaturalExpansion()) {
            return baseData.naturalExpansionPosition().toPosition();
        } else {
            return baseData.mainBasePosition().toPosition();
        }
    }

    private void trackEnemyUnits() {
        for (Unit unit: game.getAllUnits()) {
            UnitType unitType = unit.getType();

            // Check if the unit should be ignored: own unit, resource, powerup, special/unknown
            if (unit.getPlayer() == game.self()
                    || unitType.isResourceContainer()
                    || unitType.isMineralField()
                    || unitType.isNeutral()
                    || unitType == UnitType.Zerg_Lurker_Egg // Hack for neutral eggs
                    || unitType.isSpecialBuilding()
                    || unitType.isBuilding()
                    || unit.isMorphing()) {
                continue;
            }

            if (visibleEnemyUnits.size() > 0 && unitType == UnitType.Zerg_Larva) {
                continue;
            }

            visibleEnemyUnits.add(unit);
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
        BaseData baseData = gameState.getBaseData();
        ScoutData scoutData = gameState.getScoutData();
        for (Unit unit: game.getAllUnits()) {
            if (unit.getPlayer() != game.enemy()) {
                continue;
            }
            UnitType unitType = unit.getType();
            TilePosition tp = unit.getTilePosition();


            if (unit.isVisible() && unitType.isBuilding()) {
                enemyBuildings.add(unit);
                scoutData.addEnemyBuildingLocation(tp);
                if (isHostileBuildingToGround(unitType)) {
                    enemyHostileToGroundBuildings.add(unit);
                }

                // If enemyBase is unknown and this is our first time encountering an enemyUnit, set enemyBase
                if (baseData.getMainEnemyBase() == null) {
                    Base enemyMainCandidate = closestBaseToUnit(unit, new ArrayList<>(startingBasesSet));
                    // If enemy main is unknown and closest main is ours, probably a cheese
                    // TODO: Handle cheese, detect proxy
                    if (enemyMainCandidate == baseData.getMainBase()) {
                        continue;
                    }
                    baseData.addEnemyBase(enemyMainCandidate);
                }

                if (unitType.isResourceDepot() && baseData.isBaseTilePosition(tp)) {
                    Base enemyBaseCandidate = baseData.baseAtTilePosition(tp);
                    baseData.addEnemyBase(enemyBaseCandidate);
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
        ScoutData scoutData = gameState.getScoutData();
        List<TilePosition> foundBuildings = new ArrayList<>();
        for (TilePosition tilePosition: scoutData.getEnemyBuildingPositions()) {
            if (game.isVisible(tilePosition)) {
                foundBuildings.add(tilePosition);
            }
        }

        foundBuildings.stream().forEach(buildingPosition -> scoutData.removeEnemyBuildingLocation(buildingPosition));
        return;
    }

    private void ensureScoutTargets() {
        ScoutData scoutData = gameState.getScoutData();
        if (scoutData.hasScoutTargets()) {
            return;
        }
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
            if (!scoutData.hasScoutTarget(tile) && mapTile.isBuildable()) {
                scoutData.addScoutTarget(tile);
            }
        }
    }

    private void checkScoutTargets() {
        ensureScoutTargets();

        ScoutData scoutData = gameState.getScoutData();

        // Avoid ConcurrentModificationException
        List<TilePosition> foundTargets = new ArrayList<>();
        // Are we possibly sending multiple units to the same scout target?
        // And if we remove it here, then the other scout encounters a null target
        for (TilePosition target: scoutData.getActiveScoutTargets()) {
            // Bug: null targets are getting passed in! gracefully recover, but log error to out
            if (target == null) {
                scoutData.removeActiveScoutTarget(target);
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
            scoutData.removeActiveScoutTarget(target);
            scoutData.removeEnemyBuildingLocation(target);
            startingBasesTilePositions.remove(target);
            if (tilePositionToBaseLookup.containsKey(target)) {
                Base b = tilePositionToBaseLookup.get(target);
                scoutData.removeBaseScoutAssignment(b);
                tilePositionToBaseLookup.remove(target);
            }
        }
    }

    private void initBases() {
        ScoutData scoutData = gameState.getScoutData();

        for (final Base b : bwem.getMap().getBases()) {
            TilePosition tilePosition = b.getLocation();
            if (b.isStartingLocation()) {
                startingBasesTilePositions.add(tilePosition);
                tilePositionToBaseLookup.put(tilePosition, b);
                scoutData.addBaseScoutAssignment(b);
                startingBasesSet.add(b);
                scoutData.addScoutTarget(tilePosition);
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
                    mapTile = new MapTile(tp, 2, true, true, MapTileType.BASE_START);
                } else if (expansionPositions.contains(tp)) {
                    mapTile = new MapTile(tp, 1, true, true, MapTileType.BASE_EXPANSION);
                } else if (resourcePositions.contains(tp)) {
                    mapTile = new MapTile(tp, 0, false, false, MapTileType.NORMAL);
                } else {
                    mapTile = new MapTile(tp, 0, this.isBuildable(tp), this.isWalkable(tp), MapTileType.NORMAL);
                }
                gameMap.addTile(mapTile, x, y);
            }
        }

        gameState.setGameMap(gameMap);
        gameState.setBuildingPlanner(new BuildingPlanner(game, bwem, gameMap));
    }

    private boolean isWalkable(TilePosition tp) {
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

    // Iterates over all walk positions in a tile
    private boolean isBuildable(TilePosition tp) {
        if (!this.isWalkable(tp)) {
            return false;
        }
        return game.isBuildable(tp);
    }

    private void ageHeatMap() {
        ScoutData scoutData = gameState.getScoutData();
        GameMap gameMap = gameState.getGameMap();
        for (MapTile mapTile : gameMap.getHeatMap()) {
            final TilePosition mapTp = mapTile.getTile();
            if (game.isVisible(mapTp)) {
                mapTile.setScoutImportance(0);
                scoutData.removeScoutTarget(mapTp);
            }
        }
        gameMap.ageHeatMap();
    }

    private void debugEnemyTargets() {
        ScoutData scoutData = gameState.getScoutData();
        for (Unit target: enemyBuildings) {
            game.drawCircleMap(target.getPosition(), 3, Color.Yellow);
        }
        for (Unit target: visibleEnemyUnits) {
            game.drawCircleMap(target.getPosition(), 3, Color.Red);
        }
        for (TilePosition tilePosition: scoutData.getEnemyBuildingPositions()) {
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
                }
            }

            for (Unit unit: noLongerThreats) {
                unitThreats.remove(unit);
            }
        }
    }

    private void checkBaseThreats() {
        Set<Unit> visibleUnits = gameState.getDetectedEnemyUnits();
        if (visibleUnits.isEmpty()) {
            return;
        }

        Set<Base> bases = gameState.getGatherersAssignedToBase().keySet();
        HashMap<Base, HashSet<Unit>> baseThreats = gameState.getBaseToThreatLookup();
        for (Base base: bases) {
            if (!baseThreats.containsKey(base)) {
                baseThreats.put(base, new HashSet<>());
            }

            for (Unit unit: visibleUnits) {
                if (base.getLocation().toPosition().getDistance(unit.getTilePosition().toPosition()) < 256) {
                    baseThreats.get(base).add(unit);
                }
            }
        }
    }

    // TODO: Determine if ground scout can reach Scout Target
    public TilePosition pollScoutTarget(boolean allowDuplicateScoutTarget) {
        // Walk through
        BaseData baseData = gameState.getBaseData();
        ScoutData scoutData = gameState.getScoutData();
        if (baseData.getMainEnemyBase() == null && !scoutData.isEnemyBuildingLocationKnown()) {
            Base baseTarget = fetchBaseRoundRobin(scoutData.getScoutingBaseSet());
            if (baseTarget != null) {
                int assignments = scoutData.getScoutsAssignedToBase(baseTarget);
                scoutData.updateBaseScoutAssignment(baseTarget, assignments);
                return baseTarget.getLocation();
            }
        }


        if (scoutData.isEnemyBuildingLocationKnown()) {
            for (TilePosition target: scoutData.getEnemyBuildingPositions()) {
                if (!scoutData.hasScoutTarget(target) || allowDuplicateScoutTarget) {
                    return target;
                }
            }
        }

        TilePosition scoutTile = getHotScoutTile();
        if (scoutTile != null) return scoutTile;

        return scoutData.findNewActiveScoutTarget();
    }

    @Nullable
    private TilePosition getHotScoutTile() {
        GameMap gameMap = gameState.getGameMap();
        ArrayList<MapTile> heatMap = gameMap.getHeatMap();
        if (!heatMap.isEmpty()) {
            MapTile scoutTile = heatMap.get(0);
            scoutTile.setScoutImportance(0);
            gameMap.ageHeatMap();
            return scoutTile.getTile();
        }
        return null;
    }

    private Base fetchBaseRoundRobin(Set<Base> candidateBases) {
        Base leastScoutedBase = null;
        Integer fewestScouts = Integer.MAX_VALUE;
        ScoutData scoutData = gameState.getScoutData();
        for (Base base: candidateBases) {
            Integer assignedScoutsToBase = scoutData.getScoutsAssignedToBase(base);
            if (assignedScoutsToBase < fewestScouts) {
                leastScoutedBase = base;
                fewestScouts = assignedScoutsToBase;
            }

        }
        return leastScoutedBase;
    }

    // TODO: Consider learning/record
    private BuildOrder transitionBuildOrder() {
        BuildOrder active = gameState.getActiveBuildOrder();
        Set<BuildOrder> candidates = active.transition(gameState);
        return candidates.stream().findFirst().get();
    }

    private boolean isProxiedBuilding(Unit unit) {
        BaseData baseData = gameState.getBaseData();
        return isNearAnyBase(unit.getTilePosition(), baseData.getMyBases());
    }

    private boolean isNearAnyBase(TilePosition position, Set<Base> myBases) {
        for (Base base : myBases) {
            TilePosition baseLocation = base.getLocation();
            int manhattanDistance = Math.abs(baseLocation.getX() - position.getX()) +
                    Math.abs(baseLocation.getY() - position.getY());

            if (manhattanDistance <= DETECTION_DISTANCE) {
                return true;
            }
        }

        return false;
    }
}
