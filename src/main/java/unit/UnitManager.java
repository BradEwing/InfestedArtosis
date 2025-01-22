package unit;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import info.BaseData;
import info.InformationManager;
import info.GameState;
import info.ScoutData;
import org.bk.ass.sim.BWMirrorAgentFactory;
import unit.managed.ManagedUnit;
import unit.managed.ManagedUnitFactory;
import unit.managed.UnitRole;
import unit.scout.ScoutManager;
import unit.squad.SquadManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class UnitManager {

    private Game game;
    private GameState gameState;

    private ManagedUnitFactory factory;
    private BuildingManager buildingManager;
    private InformationManager informationManager;
    private ScoutManager scoutManager;
    private SquadManager squadManager;
    private WorkerManager workerManager;

    private HashMap<Unit, ManagedUnit> managedUnitLookup = new HashMap<>();
    private HashSet<ManagedUnit> managedUnits = new HashSet<>();

    public UnitManager(Game game, InformationManager informationManager, BWEM bwem, GameState gameState) {
        this.game = game;

        this.gameState = gameState;
        this.factory = new ManagedUnitFactory(game);

        this.informationManager = informationManager;
        this.workerManager = new WorkerManager(game, gameState);
        this.squadManager = new SquadManager(game, gameState, informationManager);
        this.scoutManager = new ScoutManager(game, gameState, informationManager);
        this.buildingManager = new BuildingManager(game, gameState);
        initManagedUnits();
    }

    private void initManagedUnits() {
        for (Unit unit: game.getAllUnits()) {
            if (unit.getPlayer() == game.self()) {
                UnitType unitType = unit.getType();
                ManagedUnit managedUnit = createManagedUnit(unit, UnitRole.IDLE);
                if (unitType == UnitType.Zerg_Overlord) {
                    createScout(unit, managedUnit);
                }
                if (unitType == UnitType.Zerg_Drone) {
                    createDrone(unit, managedUnit);
                }
                if (unitType == UnitType.Zerg_Larva) {
                    createLarva(unit, managedUnit);
                }
                if (unitType == UnitType.Zerg_Hatchery) {
                    createBuilding(unit, managedUnit);
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

        buildingManager.onFrame();
        workerManager.onFrame();
        squadManager.updateOverlordSquad();
        squadManager.updateFightSquads();
        squadManager.updateDefenseSquads();
        scoutManager.onFrame();

        for (ManagedUnit managedUnit: managedUnits) {
            // Check if unready units can be ready again
            if (!managedUnit.isReady() && game.getFrameCount() >= managedUnit.getUnreadyUntilFrame()) {
                managedUnit.setReady(true);
            }

            UnitRole role = managedUnit.getRole();

            switch(role) {
                case GATHER:
                case BUILD:
                case MORPH:
                case LARVA:
                case RETREAT:
                case DEFEND:
                case BUILDING:
                case RALLY:
                    managedUnit.execute();
                    continue;
            }

            switch(managedUnit.getUnitType()) {
                case Zerg_Drone:
                    onFrameDrone(managedUnit, role);
                    break;
                default:
                    onFrameDefault(managedUnit, role);
                    break;
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



        // Consider case where we are already tracking the unit that morphed
        // TODO: handle case where managed unit type has changed
        if (managedUnitLookup.containsKey(unit)) {
            return;
        }

        ManagedUnit managedUnit = createManagedUnit(unit, UnitRole.IDLE);

        // For now, return early if drone or building
        UnitType unitType = unit.getType();
        if (unitType.isBuilding()) {
            createBuilding(unit, managedUnit);
            return;
        }

        ScoutData scoutData = gameState.getScoutData();

        // Assign scouts if we don't know where enemy is
        if (unitType == UnitType.Zerg_Drone || unitType == UnitType.Zerg_Larva) {
            if (scoutManager.needDroneScout()) {
                scoutManager.addScout(managedUnit);
            } else {
                workerManager.onUnitComplete(managedUnit);
            }
        } else if (unitType == UnitType.Zerg_Overlord || informationManager.getEnemyBuildings().size() + informationManager.getVisibleEnemyUnits().size() == 0) {
            if (unitType == UnitType.Zerg_Overlord && scoutData.isEnemyBuildingLocationKnown()) {
                squadManager.addManagedUnit(managedUnit);
                scoutManager.removeScout(managedUnit);
                return;
            } else {
                createScout(unit, managedUnit);
            }
        } else {
            managedUnit.setRole(UnitRole.FIGHT);
            squadManager.addManagedUnit(managedUnit);
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
        if (managedUnit == null) {
            return;
        }

        buildingManager.remove(managedUnit);
        workerManager.removeManagedWorker(managedUnit);
        managedUnits.remove(managedUnit);
        squadManager.removeManagedUnit(managedUnit);
        scoutManager.removeScout(managedUnit);
        managedUnitLookup.remove(unit);
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
        squadManager.onUnitDestroy(unit);
    }

    public void onUnitMorph(Unit unit) {
        if (unit.getType() == UnitType.Zerg_Larva && !managedUnitLookup.containsKey(unit)) {
            workerManager.onUnitComplete(createManagedUnit(unit, UnitRole.LARVA));
            return;
        }

        if (!managedUnitLookup.containsKey(unit)) {
            return;
        }

        ManagedUnit managedUnit = managedUnitLookup.get(unit);
        final UnitType type = unit.getType();

        if (type != managedUnit.getUnitType()) {
            managedUnit.setUnitType(unit.getType());
        }

        if (type.isBuilding()) {
            removeManagedUnit(unit);
            createBuilding(unit, managedUnit);
            return;
        }

        if (type == UnitType.Zerg_Overlord) {
            managedUnit.setCanFight(false);
        }
        if (type == UnitType.Zerg_Drone) {
            if (scoutManager.needDroneScout()) {
                scoutManager.addScout(managedUnit);
            } else {
                workerManager.onUnitMorph(managedUnit);
            }
        }
    }

    /**
     * Handle drone transitions to other Managers
     *
     * TODO: Scout harass
     * TODO: Scout until danger / suicide scout
     *
     * @param unit ManagedUnit
     * @param role UnitRole
     */
    private void onFrameDrone(ManagedUnit unit, UnitRole role) {
        if (role == UnitRole.SCOUT && scoutManager.endDroneScout()) {
            scoutManager.removeScout(unit);
            workerManager.addManagedWorker(unit);
        }
    }

    private void onFrameDefault(ManagedUnit managedUnit, UnitRole role) {
        // TODO: Refactor
        // If an enemy building location is known, retreat overlords to bases
        // TODO: Only retreat if there are things that can harm the overlord
        ScoutData scoutData = gameState.getScoutData();
        if (managedUnit.getUnitType() == UnitType.Zerg_Overlord && role == UnitRole.SCOUT && scoutData.isEnemyBuildingLocationKnown()) {
            squadManager.addManagedUnit(managedUnit);
            scoutManager.removeScout(managedUnit);
        }

        if (managedUnit.canFight() && role != UnitRole.FIGHT && informationManager.isEnemyLocationKnown()) {
            managedUnit.setRole(UnitRole.FIGHT);
            squadManager.addManagedUnit(managedUnit);
            scoutManager.removeScout(managedUnit);
        } else if (role != UnitRole.SCOUT && !informationManager.isEnemyUnitVisible() && informationManager.getEnemyBuildings().size() == 0) {
            scoutManager.addScout(managedUnit);
            squadManager.removeManagedUnit(managedUnit);
        }
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

    private ManagedUnit createManagedUnit(Unit unit, UnitRole role) {
        ManagedUnit managedUnit = factory.create(unit, role);
        managedUnitLookup.put(unit, managedUnit);
        managedUnits.add(managedUnit);
        return managedUnit;
    }

    private void createScout(Unit unit, ManagedUnit managedUnit) {
        managedUnit.setRole(UnitRole.SCOUT);
        scoutManager.addScout(managedUnit);

        if (unit.getType() == UnitType.Zerg_Overlord) {
            managedUnit.setCanFight(false);
        }
        return;
    }

    private void createDrone(Unit unit, ManagedUnit managedUnit) {
        managedUnits.add(managedUnit);
        workerManager.onUnitComplete(managedUnit);
        managedUnitLookup.put(unit, managedUnit);
    }

    private void createLarva(Unit unit, ManagedUnit managedUnit) {
        managedUnit.setRole(UnitRole.LARVA);
        managedUnits.add(managedUnit);
        workerManager.onUnitComplete(managedUnit);
        managedUnitLookup.put(unit, managedUnit);
    }

    private void createBuilding(Unit unit, ManagedUnit managedUnit) {
        managedUnit.setRole(UnitRole.BUILDING);
        managedUnit.setCanFight(false);
        managedUnits.add(managedUnit);
        buildingManager.add(managedUnit);
        managedUnitLookup.put(unit, managedUnit);
    }
}
