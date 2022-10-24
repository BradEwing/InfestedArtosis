package unit;

import bwapi.Game;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import bwem.CPPath;
import bwem.ChokePoint;
import info.InformationManager;
import info.GameState;
import org.bk.ass.sim.BWMirrorAgentFactory;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import unit.squad.SquadManager;
import util.UnitDistanceComparator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class UnitManager {

    private Game game;
    private BWEM bwem;

    private GameState gameState;

    private BWMirrorAgentFactory agentFactory;

    // Take a dependency on informationManager for now
    // TODO: Pass GameState here to make decisions for units
    //
    // Should workers go here? Probably, especially if they are pulled for base defense
    private InformationManager informationManager;
    private WorkerManager workerManager;
    private SquadManager squadManager;

    // TODO: refactor
    private HashMap<Unit, ManagedUnit> managedUnitLookup = new HashMap<>();

    private HashSet<ManagedUnit> workers = new HashSet<>();
    private HashSet<ManagedUnit> scouts = new HashSet<>();
    private HashSet<ManagedUnit> fighters = new HashSet<>();
    private HashSet<ManagedUnit> managedUnits = new HashSet<>();

    public UnitManager(Game game, InformationManager informationManager, BWEM bwem, GameState gameState) {
        this.game = game;
        this.informationManager = informationManager;
        this.bwem = bwem;
        this.gameState = gameState;
        this.agentFactory = new BWMirrorAgentFactory();

        this.workerManager = new WorkerManager(game, gameState);
        this.squadManager = new SquadManager(game, gameState, informationManager);
        initManagedUnits();
    }

    // TODO: Assign starting drones here as well, then pass to econ manager?
    private void initManagedUnits() {
        for (Unit unit: game.getAllUnits()) {
            if (unit.getPlayer() == game.self()) {
                UnitType unitType = unit.getType();
                ManagedUnit managedUnit = new ManagedUnit(game, unit, UnitRole.IDLE);
                if (unitType == UnitType.Zerg_Overlord) {
                    managedUnit.setRole(UnitRole.SCOUT);
                    managedUnits.add(managedUnit);
                    managedUnitLookup.put(unit, managedUnit);
                    managedUnit.setCanFight(false);
                }
                if (unitType == UnitType.Zerg_Drone) {
                    managedUnits.add(managedUnit);
                    workerManager.onUnitComplete(managedUnit);
                    managedUnitLookup.put(unit, managedUnit);
                }
                if (unitType == UnitType.Zerg_Larva) {
                    managedUnit.setRole(UnitRole.LARVA);
                    managedUnits.add(managedUnit);
                    workerManager.onUnitComplete(managedUnit);
                    managedUnitLookup.put(unit, managedUnit);
                }
            }
        }
    }

    public void onFrame() {
        int frameCount = game.getFrameCount();
        // Wait a frame to let WorkerManager assign roles
        if (frameCount < 1) {
            return;
        }

        checkBaseThreats();

        workerManager.onFrame();
        squadManager.updateFightSquads();
        squadManager.updateDefenseSquads();

        for (ManagedUnit managedUnit: managedUnits) {
            /**
             * Disable for now, there's a non-deterministic crash with BWEM and units get stuck in their move path
            if (managedUnit.getPathToTarget() == null || managedUnit.getPathToTarget().size() == 0) {
                calculateMovementPath(managedUnit);
            }
             */
            // Check if unready units can be ready again
            if (!managedUnit.isReady() && game.getFrameCount() >= managedUnit.getUnreadyUntilFrame()) {
                managedUnit.setReady(true);
            }

            UnitRole role = managedUnit.getRole();

            if (role == UnitRole.GATHER || role == UnitRole.BUILD || role == UnitRole.MORPH || role == UnitRole.LARVA || role == UnitRole.RETREAT || role == UnitRole.DEFEND) {
                // TODO: Fix, this is hacky
                // Reassignment from one role to another should be handled elsewhere
                managedUnit.execute();
                continue;
            }

            if (managedUnit.isCanFight() && role != UnitRole.FIGHT && informationManager.isEnemyLocationKnown() && informationManager.isEnemyUnitVisible()) {
                managedUnit.setRole(UnitRole.FIGHT);
                squadManager.addManagedUnit(managedUnit);
            } else if (role != UnitRole.SCOUT && !informationManager.isEnemyUnitVisible()) {
                reassignToScout(managedUnit);
                squadManager.removeManagedUnit(managedUnit);
            }

            // TODO: Refactor
            // Check every frame for closest enemy for unit
            if (role == UnitRole.FIGHT) {
                assignClosestEnemyToManagedUnit(managedUnit);
            }

            if (role == UnitRole.SCOUT && managedUnit.getMovementTargetPosition() == null) {
                assignScoutMovementTarget(managedUnit);
            }

            managedUnit.execute();
        }
    }

    public void onUnitShow(Unit unit) {
        if (unit.getPlayer() != game.self()) {
            return;
        }

        if (unit.getType() == UnitType.Zerg_Larva && !managedUnitLookup.containsKey(unit)) {
            ManagedUnit managedUnit = createManagedUnit(unit, UnitRole.LARVA);
            workerManager.onUnitComplete(managedUnit);
            return;
        }

        // For now, return early if drone or building
        UnitType unitType = unit.getType();
        // TODO: Buildings, why not? Useful when tracking precise morphs
        // TODO: Building planner
        if (unitType.isBuilding()) {
            return;
        }

        // Consider case where we are already tracking the unit that morphed
        // TODO: handle case where managed unit type has changed
        if (managedUnitLookup.containsKey(unit)) {
            return;
        }

        // Assign scouts if we don't know where enemy is
        if (unitType == UnitType.Zerg_Drone || unitType == UnitType.Zerg_Larva) {
            ManagedUnit managedWorker = new ManagedUnit(game, unit, UnitRole.IDLE);
            workerManager.onUnitComplete(managedWorker);
        } else if (unitType == UnitType.Zerg_Overlord || informationManager.getEnemyBuildings().size() + informationManager.getEnemyUnits().size() == 0) {
            createScout(unit);
        } else {
            ManagedUnit managedFighter = new ManagedUnit(game, unit, UnitRole.FIGHT);
            squadManager.addManagedUnit(managedFighter);
            assignClosestEnemyToManagedUnit(managedFighter);
            managedUnitLookup.put(unit, managedFighter);
            managedUnits.add(managedFighter);
            return;

        }
    }

    public void onUnitComplete(Unit unit) {
        // For now, return early if drone or building
        UnitType unitType = unit.getType();
        // TODO: Buildings, why not? Useful when tracking precise morphs
        // TODO: Building planner
        // TODO: A matcher dispatcher to determine which units are passed to which manger?
        if (unitType.isBuilding()) {
            if (unitType == UnitType.Zerg_Extractor) {
                workerManager.onExtractorComplete();
            }
            return;
        }

        // Consider case where we are already tracking the unit that morphed
        // TODO: handle case where managed unit type has changed
        if (!managedUnitLookup.containsKey(unit)) {
            return;
        }

        ManagedUnit managedUnit = managedUnitLookup.get(unit);
        if (managedUnit.getRole() == UnitRole.MORPH) {
            managedUnit.setRole(UnitRole.IDLE);
        }
    }

    private void removeManagedUnit(Unit unit) {
        ManagedUnit managedUnit = managedUnitLookup.get(unit);
        workerManager.removeManagedWorker(managedUnit);
        managedUnits.remove(managedUnit);
        managedUnitLookup.remove(unit);
        squadManager.removeManagedUnit(managedUnit);

        if (managedUnit == null) {
            return;
        }

        TilePosition movementTarget = managedUnit.getMovementTargetPosition();
        HashSet<TilePosition> activeScoutTargets =  informationManager.getActiveScoutTargets();
        if (movementTarget != null && activeScoutTargets.contains(managedUnit.getMovementTargetPosition())) {
            activeScoutTargets.remove(movementTarget);
        }
    }

    public void onUnitDestroy(Unit unit) {
        UnitType unitType = unit.getType();
        if (unitType == UnitType.Resource_Mineral_Field) {
            workerManager.removeMineral(unit);
            return;
        } else if (unitType == UnitType.Zerg_Extractor) {
            workerManager.removeGeyser(unit);
            return;
        }
        removeManagedUnit(unit);
    }

    public void onUnitMorph(Unit unit) {
        if (unit.getType() == UnitType.Zerg_Larva && !managedUnitLookup.containsKey(unit)) {
            workerManager.onUnitComplete(createManagedUnit(unit, UnitRole.LARVA));
            return;
        }

        // TODO: BuildingManager
        //   - Static D
        if (managedUnitLookup.containsKey(unit) && unit.getType().isBuilding()) {
            removeManagedUnit(unit);
            return;
        }

        if (!managedUnitLookup.containsKey(unit)) {
            return;
        }

        ManagedUnit managedUnit = managedUnitLookup.get(unit);

        // TODO: Managed Buildings
        if (unit.getType() == UnitType.Buildings) {
            workerManager.removeManagedWorker(managedUnit);
            managedUnits.remove(managedUnit);
            managedUnitLookup.remove(unit);
            return;
        }

        if (unit.getType() != managedUnit.getUnitType()) {
            managedUnit.setUnitType(unit.getType());
            //managedUnit.setRole(UnitRole.IDLE);
        }

        if (unit.getType() == UnitType.Zerg_Overlord) {
            managedUnit.setCanFight(false);
        }
        workerManager.onUnitMorph(managedUnit);
    }

    private void checkBaseThreats() {
        HashMap<Base, HashSet<Unit>> baseThreats = this.gameState.getBaseToThreatLookup();
        for (Base base: baseThreats.keySet()) {
            if (baseThreats.get(base).size() > 0) {
                assignGatherersToDefense(base);
            } else {
                assignDefendersToGather(base);
            }
        }
    }

    private void assignGatherersToDefense(Base base) {
        HashSet<ManagedUnit> gatherersAssignedToBase = this.gameState.getGatherersAssignedToBase().get(base);
        if (gatherersAssignedToBase.size() == 0) {
            return;
        }
        List<Unit> threateningUnits = this.gameState.getBaseToThreatLookup()
                .get(base)
                .stream()
                .collect(Collectors.toList());



        List<ManagedUnit> gatherersToReassign = this.squadManager.assignGathererDefenders(base, gatherersAssignedToBase, threateningUnits);
        for (ManagedUnit managedUnit: gatherersToReassign) {
            this.workerManager.removeManagedWorker(managedUnit);
        }
    }

    private void assignDefendersToGather(Base base) {
        HashSet<ManagedUnit> gatherersAssignedToBase = this.gameState.getGatherersAssignedToBase().get(base);
        List<ManagedUnit> gatherersToReassign = this.squadManager.disbandDefendSquad(base);

        for (ManagedUnit managedUnit: gatherersToReassign) {
            this.workerManager.addManagedWorker(managedUnit);
        }
    }

    private void calculateMovementPath(ManagedUnit managedUnit) {
        // Don't calculate a new path if we have one
        if (managedUnit.getPathToTarget() != null && managedUnit.getPathToTarget().size() > 0) {
            return;
        }
        TilePosition movementTargetPosition = managedUnit.getMovementTargetPosition();
        if (movementTargetPosition == null) {
            return;
        }

        TilePosition currentPosition = managedUnit.getUnit().getTilePosition();

        CPPath path = bwem.getMap().getPath(currentPosition.toPosition(), movementTargetPosition.toPosition());
        List<TilePosition> pathToTarget = new ArrayList<>();
        for (ChokePoint chokePoint: path) {
            pathToTarget.add(chokePoint.getCenter().toTilePosition());
        }
        managedUnit.setPathToTarget(pathToTarget);

    }

    private void assignClosestEnemyToManagedUnit(ManagedUnit managedUnit) {
        List<Unit> enemyUnits = new ArrayList<>();
        enemyUnits.addAll(informationManager.getEnemyUnits());
        enemyUnits.addAll(informationManager.getEnemyBuildings());

        if (enemyUnits.size() > 0) {
            // Try to assign an enemy target. If none of the enemies are valid fight targets, fall back to the scout target.
            managedUnit.assignClosestEnemyAsFightTarget(enemyUnits, informationManager.pollScoutTarget(true));
        }
    }

    private ManagedUnit createManagedUnit(Unit unit, UnitRole role) {
        ManagedUnit managedUnit = new ManagedUnit(game, unit, role);
        managedUnitLookup.put(unit, managedUnit);
        managedUnits.add(managedUnit);
        return managedUnit;
    }

    private void reassignToScout(ManagedUnit managedUnit) {
        managedUnit.setRole(UnitRole.SCOUT);
        assignScoutMovementTarget(managedUnit);
        squadManager.removeManagedUnit(managedUnit);
    }

    private void assignScoutMovementTarget(ManagedUnit managedUnit) {
        if (managedUnit.getMovementTargetPosition() != null) {
            if (!game.isVisible(managedUnit.getMovementTargetPosition())) {
                return;
            }
            managedUnit.setMovementTargetPosition(null);
        }

        TilePosition target = informationManager.pollScoutTarget(false);
        informationManager.setActiveScoutTarget(target);
        managedUnit.setMovementTargetPosition(target);
    }

    private void createScout(Unit unit) {
        ManagedUnit managedScout = new ManagedUnit(game, unit, UnitRole.SCOUT);
        managedUnitLookup.put(unit, managedScout);
        managedUnits.add(managedScout);
        assignScoutMovementTarget(managedScout);

        if (unit.getType() == UnitType.Zerg_Overlord) {
            managedScout.setCanFight(false);
        }
        return;
    }
}
