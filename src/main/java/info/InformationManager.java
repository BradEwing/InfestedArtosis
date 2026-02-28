package info;

import bwapi.Game;
import bwapi.Player;
import bwapi.PlayerType;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwem.BWEM;
import bwem.Base;
import bwem.Geyser;
import bwem.Mineral;
import info.map.BuildingPlanner;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileType;
import info.tracking.ObservedBulletTracker;
import static util.Distance.manhattanTileDistance;
import info.tracking.ObservedUnitTracker;
import learning.LearningManager;
import macro.plan.Plan;
import macro.plan.PlanType;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class InformationManager {
    private BWEM bwem;
    private Game game;

    private GameState gameState;

    private BaseManager baseManager;
    private LearningManager learningManager;

    // TODO: Move to GameState
    private HashSet<Base> startingBasesSet = new HashSet<>();
    private HashSet<Base> expansionBasesSet = new HashSet<>();

    private static final int PROXY_DETECTION_DISTANCE = 24;

    private boolean isGasStructure(UnitType unitType) {
        return unitType == UnitType.Terran_Refinery 
            || unitType == UnitType.Zerg_Extractor 
            || unitType == UnitType.Protoss_Assimilator;
    }


    public InformationManager(BWEM bwem, Game game, GameState gameState, LearningManager learningManager) {
        this.bwem = bwem;
        this.game = game;
        this.gameState = gameState;
        this.learningManager = learningManager;

        initBases();
        initializeGameMap();

        this.baseManager = new BaseManager(bwem, game, gameState);
    }

    public void onFrame() {
        gameState.onFrame();
        ageHeatMap();

        trackEnemyUnits();
        trackEnemyBuildings();
        checkEnemyBases();
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
    }

    /**
     * Update GameState's tech progression to enable new units, tech or upgrades.
     *
     * @param unitType tech building
     */
    public void updateTechProgression(UnitType unitType) {
        TechProgression techProgression = gameState.getTechProgression();

        switch (unitType) {
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
                techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() - 1);
                techProgression.setEvolutionChambers(techProgression.evolutionChambers() + 1);
                break;
            case Zerg_Queens_Nest:
                techProgression.setQueensNest(true);
                break;
            case Zerg_Hive:
                techProgression.setHive(true);
                break;
            default:
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
        // Exception: Allow gas structures (Refinery, Extractor, Assimilator) to be tracked
        if (unitType.isResourceContainer() && !isGasStructure(unitType)
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
    }

    public void onUnitMorph(Unit unit) {
        HashMap<Unit, Plan> assignedPlannedItems = gameState.getAssignedPlannedItems();
        Plan assignedPlan = assignedPlannedItems.get(unit);
        
        if (assignedPlan == null) {
            return;
        }

        UnitType plannedUnit = assignedPlan.getPlannedUnit();
        if (assignedPlan.getType() == PlanType.BUILDING) {
            UnitTypeCount count = gameState.getUnitTypeCount();
            // TODO: Greater Spire
            if (plannedUnit == UnitType.Zerg_Sunken_Colony || plannedUnit == UnitType.Zerg_Spore_Colony) {
                count.removeUnit(UnitType.Zerg_Creep_Colony);
            } else if (plannedUnit == UnitType.Zerg_Lair) {
                count.removeUnit(UnitType.Zerg_Hatchery);
            } else {
                count.removeUnit(UnitType.Zerg_Drone);
            }

            updateTechProgression(plannedUnit);
        } else if (assignedPlan.getType() == PlanType.UNIT) {
            UnitTypeCount count = gameState.getUnitTypeCount();
            if (plannedUnit == UnitType.Zerg_Lurker) {
                count.removeUnit(UnitType.Zerg_Hydralisk);
            }
        }
    }

    public void onUnitComplete(Unit unit) {
        UnitType unitType = unit.getType();
        UnitTypeCount unitCount = gameState.getUnitTypeCount();
        unitCount.addUnit(unitType);
        unitCount.unplanUnit(unitType);

        Player self = game.self();
        if (unit.getPlayer() != self) {
            return;
        }

        if (unitType == UnitType.Zerg_Extractor) {
            gameState.setGeyserAssignment(unit);
        }

        ResourceCount resourceCount = gameState.getResourceCount();
        final int plannedSupply = resourceCount.getPlannedSupply();
        if (unitType == UnitType.Zerg_Overlord) {
            resourceCount.setPlannedSupply(Math.max(0, plannedSupply - unitType.supplyProvided()));
        }

        if (unitType == UnitType.Zerg_Hatchery) {
            // Account for macro hatch
            BaseData baseData = gameState.getBaseData();
            if (baseData.isBaseTilePosition(unit.getTilePosition())) {
                gameState.claimBase(unit);
            } else {
                gameState.addMacroHatchery(unit);
            }

            gameState.removePlannedHatchery(1);
            if (gameState.getPlannedHatcheries() < 0) {
                gameState.setPlannedHatcheries(0);
            }
        }

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

        switch (unitType) {
            case Zerg_Spawning_Pool:
                techProgression.setSpawningPool(false);
                techProgression.setPlannedSpawningPool(false);
                break;
            case Zerg_Hydralisk_Den:
                techProgression.setHydraliskDen(false);
                techProgression.setPlannedDen(false);
                break;
            case Zerg_Spire:
                techProgression.setSpire(false);
                techProgression.setPlannedSpire(false);
                break;
            case Zerg_Lair:
                techProgression.setLair(false);
                techProgression.setPlannedLair(false);
                break;
            case Zerg_Evolution_Chamber:
                final int evolutionChambers = techProgression.getEvolutionChambers();
                techProgression.setEvolutionChambers(evolutionChambers - 1);
                break;
            case Zerg_Queens_Nest:
                techProgression.setQueensNest(false);
                techProgression.setPlannedQueensNest(false);
                break;
            case Zerg_Hive:
                techProgression.setHive(false);
                techProgression.setPlannedHive(false);
                break;
            default:
                break;
        }
    }

    public void onUnitDestroy(Unit unit) {
        UnitType unitType = unit.getType();

        if (unit.getPlayer() == game.enemy() && unitType.isBuilding()) {
            ScoutData scoutData = gameState.getScoutData();
            scoutData.removeEnemyBuildingLocation(unit.getTilePosition());
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
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        for (Unit enemy: tracker.getVisibleEnemyUnits()) {
            if (enemy.isDetected()) {
                return true;
            }
        }
        for (Unit enemy: tracker.getBuilding()) {
            if (enemy.isVisible()) {
                return true;
            }
        }

        return false;
    }

    public int getEnemyHostileToGroundBuildingsCount() {
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        return tracker.getHostileToGroundBuildings().size();
    }

    public int getLastEnemyUnitSeenFrame() {
        return gameState.getLastEnemyUnitSeenFrame();
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
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        ObservedBulletTracker observedBulletTracker = gameState.getObservedBulletTracker();

        observedBulletTracker.onFrame(game, game.getFrameCount());
        boolean sawEnemyUnit = false;

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

            if (tracker.getVisibleEnemyUnits().size() > 0 && unitType == UnitType.Zerg_Larva) {
                continue;
            }

            // Track if we see any non-building enemy unit
            if (unit.isVisible() && unit.getPlayer() == game.enemy() && !unitType.isBuilding()) {
                sawEnemyUnit = true;
            }

            // Idempotently track enemy unit - only calls onUnitShow if not already tracked
            boolean isProxied = isProxiedBuilding(unit);
            tracker.onUnitShow(unit, game.getFrameCount(), isProxied);
        }

        if (sawEnemyUnit) {
            gameState.setLastEnemyUnitSeenFrame(game.getFrameCount());
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
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();

        for (Unit unit: game.getAllUnits()) {
            if (unit.getPlayer() != game.enemy()) {
                continue;
            }
            UnitType unitType = unit.getType();
            TilePosition tp = unit.getTilePosition();


            if (unit.isVisible() && unitType.isBuilding()) {
                // Idempotently track enemy building - only calls onUnitShow if not already tracked
                boolean isProxied = isProxiedBuilding(unit);
                tracker.onUnitShow(unit, game.getFrameCount(), isProxied);

                scoutData.addEnemyBuildingLocation(tp);

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
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        for (Unit building: tracker.getBuilding()) {
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

    private void checkEnemyBases() {
        BaseData baseData = gameState.getBaseData();
        List<Base> basesToRemove = new ArrayList<>();
        for (Base base : baseData.getEnemyBases()) {
            if (!game.isVisible(base.getLocation())) {
                continue;
            }
            boolean hasEnemyBuildings = game.getUnitsInRadius(base.getCenter(), 256).stream()
                    .anyMatch(u -> u.getPlayer() == game.enemy() && u.getType().isBuilding());
            if (!hasEnemyBuildings) {
                basesToRemove.add(base);
            }
        }
        for (Base base : basesToRemove) {
            baseData.removeEnemyBase(base);
        }
    }

    private void ensureScoutTargets() {
        ScoutData scoutData = gameState.getScoutData();
        // Always try to add new targets if we have few available or none at all
        if (scoutData.hasScoutTargets() && scoutData.getScoutTargets().size() > 2) {
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

        for (TilePosition target: foundTargets) {
            scoutData.removeActiveScoutTarget(target);
            scoutData.removeEnemyBuildingLocation(target);
        }

        List<Base> foundBases = new ArrayList<>();
        for (Base base : scoutData.getScoutingBaseSet()) {
            TilePosition tp = base.getLocation();
            if (game.isVisible(tp)) {
                foundBases.add(base);
            }
        }
        for (Base base : foundBases) {
            scoutData.clearScoutedBase(base);
        }
    }

    private void initBases() {
        ScoutData scoutData = gameState.getScoutData();

        for (final Base b : bwem.getMap().getBases()) {
            if (b.isStartingLocation()) {
                scoutData.addBaseScoutAssignment(b);
                startingBasesSet.add(b);
                scoutData.addScoutTarget(b.getLocation());
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

        GameMap gameMap = gameState.getGameMap();

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

        // Calculate accessible WalkPositions from a main base using flood fill
        if (!startingPositions.isEmpty()) {
            TilePosition mainBasePosition = startingPositions.iterator().next();
            gameMap.calculateAccessibleWalkPositions(game, mainBasePosition);
        }

        gameState.setBuildingPlanner(new BuildingPlanner(game, bwem));

        HashSet<Unit> baseMineralUnits = new HashSet<>();
        for (Base base : bwem.getMap().getBases()) {
            for (Mineral mineral : base.getMinerals()) {
                baseMineralUnits.add(mineral.getUnit());
            }
        }

        for (Unit unit : game.getStaticNeutralUnits()) {
            if (unit.getType().isMineralField() && !baseMineralUnits.contains(unit)) {
                gameMap.addBlockingMineral(unit);
            }
        }
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

    private void debugEnemyTargets() { }

    private void ensureEnemyUnitRemovedFromBaseThreats(Unit unit) {
        for (HashSet<Unit> baseThreat: gameState.getBaseToThreatLookup().values()) {
            if (baseThreat.contains(unit)) {
                baseThreat.remove(unit);
            }
        }
    }

    private void checkIfEnemyUnitsStillThreatenBase() {
        HashMap<Base, HashSet<Unit>> baseThreats = gameState.getBaseToThreatLookup();
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        int threatRadius = gameState.isCannonRushed() ? 20 : 8;
        for (Base base: baseThreats.keySet()) {
            HashSet<Unit> unitThreats = baseThreats.get(base);
            List<Unit> noLongerThreats = new ArrayList<>();
            TilePosition baseTile = base.getLocation();
            for (Unit unit: unitThreats) {
                if (unit.getType() == UnitType.Unknown) {
                    noLongerThreats.add(unit);
                    continue;
                }
                Position unitPos = tracker.getLastKnownPosition(unit);
                if (unitPos == null) {
                    noLongerThreats.add(unit);
                    continue;
                }
                if (manhattanTileDistance(baseTile, unitPos.toTilePosition()) > threatRadius) {
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

        Set<Base> bases = new HashSet<>(gameState.getGatherersAssignedToBase().keySet());
        HashMap<Base, HashSet<Unit>> baseThreats = gameState.getBaseToThreatLookup();
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();

        boolean isCannonRushed = gameState.isCannonRushed();
        Set<Unit> proxiedBuildings = isCannonRushed ? tracker.getProxiedBuildings() : null;

        if (isCannonRushed) {
            bases.addAll(gameState.getBaseData().getReservedBases());
        }

        for (Base base: bases) {
            if (!baseThreats.containsKey(base)) {
                baseThreats.put(base, new HashSet<>());
            }

            int baseThreatRadius = isCannonRushed ? 16 : 8;
            TilePosition baseTile = base.getLocation();

            for (Unit unit: visibleUnits) {
                if (manhattanTileDistance(baseTile, unit.getTilePosition()) < baseThreatRadius) {
                    baseThreats.get(base).add(unit);
                }
            }

            if (isCannonRushed) {
                for (Unit building : proxiedBuildings) {
                    Position buildingPos = tracker.getLastKnownPosition(building);
                    if (buildingPos == null) {
                        continue;
                    }
                    if (manhattanTileDistance(baseTile, buildingPos.toTilePosition()) < baseThreatRadius) {
                        baseThreats.get(base).add(building);
                    }
                }

                Set<Unit> nearbyWorkers = tracker.getWorkerUnitsNearPositions(Collections.singleton(base.getCenter()), 512);
                baseThreats.get(base).addAll(nearbyWorkers);
            }
        }
    }

    private BuildOrder transitionBuildOrder() {
        BuildOrder active = gameState.getActiveBuildOrder();
        Set<BuildOrder> candidates = active.transition(gameState);
        return learningManager.determineBuildOrder(candidates);
    }

    private boolean isProxiedBuilding(Unit unit) {
        BaseData baseData = gameState.getBaseData();
        Set<Base> basesToCheck = new HashSet<>(baseData.getMyBases());
        Base naturalBase = baseData.getInferredNaturalBase();
        if (naturalBase != null) {
            basesToCheck.add(naturalBase);
        }
        return isNearAnyBase(unit.getTilePosition(), basesToCheck);
    }

    private boolean isNearAnyBase(TilePosition position, Set<Base> myBases) {
        for (Base base : myBases) {
            TilePosition baseLocation = base.getLocation();
            if (manhattanTileDistance(baseLocation, position) <= PROXY_DETECTION_DISTANCE) {
                return true;
            }
        }

        return false;
    }
}
