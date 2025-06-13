package unit;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import info.GameState;
import info.InformationManager;
import info.ScoutData;
import info.UnitTypeCount;
import lombok.Getter;
import unit.managed.ManagedUnit;
import unit.managed.ManagedUnitFactory;
import unit.managed.UnitRole;
import unit.scout.ScoutManager;
import unit.squad.SquadManager;
import util.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class UnitManager {

    private Game game;
    private GameState gameState;

    private ManagedUnitFactory factory;
    private BuildingManager buildingManager;
    private InformationManager informationManager;
    @Getter
    private ScoutManager scoutManager;
    @Getter
    private SquadManager squadManager;
    private WorkerManager workerManager;

    private HashMap<Unit, ManagedUnit> managedUnitLookup;
    private HashSet<ManagedUnit> managedUnits;

    public UnitManager(Game game, InformationManager informationManager, BWEM bwem, GameState gameState) {
        this.game = game;

        this.gameState = gameState;
        this.factory = new ManagedUnitFactory(game);

        this.informationManager = informationManager;
        this.workerManager = new WorkerManager(game, gameState);
        this.squadManager = new SquadManager(game, gameState, informationManager);
        this.scoutManager = new ScoutManager(game, gameState, informationManager);
        this.buildingManager = new BuildingManager(game, gameState);
        this.managedUnitLookup = gameState.getManagedUnitLookup();
        this.managedUnits = gameState.getManagedUnits();
        initManagedUnits();
    }

    private void initManagedUnits() {
        for (Unit unit: game.getAllUnits()) {
            if (unit.getPlayer() == game.self()) {
                UnitType unitType = unit.getType();
                ManagedUnit managedUnit = createManagedUnit(unit, UnitRole.IDLE);
                if (unitType == UnitType.Zerg_Overlord) {
                    createScout(managedUnit);
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
                case REGROUP:
                    managedUnit.execute();
                    continue;
            }
            UnitType type = managedUnit.getUnitType();
            switch(type) {
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

        ManagedUnit managedUnit;

        if (unit.getType() == UnitType.Zerg_Larva && !managedUnitLookup.containsKey(unit)) {
            managedUnit = createManagedUnit(unit, UnitRole.LARVA);
            workerManager.onUnitComplete(managedUnit);
            return;
        }

        // Consider case where we are already tracking the unit that morphed
        if (managedUnitLookup.containsKey(unit)) {
            managedUnit = managedUnitLookup.get(unit);
        } else {
            managedUnit = createManagedUnit(unit, UnitRole.IDLE);
        }

        if (unit.getType() != managedUnit.getUnitType()) {
            UnitTypeCount unitTypeCount = gameState.getUnitTypeCount();
            unitTypeCount.removeUnit(managedUnit.getUnitType());
            unitTypeCount.addUnit(unit.getType());
            managedUnit.setUnitType(unit.getType());
        }

        // For now, return early if drone or building
        UnitType unitType = unit.getType();
        if (unitType.isBuilding()) {
            createBuilding(unit, managedUnit);
            return;
        }

        assignManagedUnit(managedUnit);
    }

    private void assignManagedUnit(ManagedUnit managedUnit) {
        ScoutData scoutData = gameState.getScoutData();
        UnitType unitType = managedUnit.getUnitType();
        UnitRole role = managedUnit.getRole();
        if (role != UnitRole.IDLE) {
            return;
        }

        // Handle Drones and Larvae first
        if (unitType == UnitType.Zerg_Larva) {
            workerManager.onUnitComplete(managedUnit);
            return;
        }
        if (unitType == UnitType.Zerg_Drone) {
            if (scoutManager.needDroneScout()) {
                createScout(managedUnit);
            } else {
                workerManager.onUnitComplete(managedUnit);
            }
            return;
        }

        // Handle Overlords as scouts if no building locations known
        if (unitType == UnitType.Zerg_Overlord) {
            if (scoutData.isEnemyBuildingLocationKnown()) {
                squadManager.addManagedUnit(managedUnit);
                scoutManager.removeScout(managedUnit);
            } else {
                createScout(managedUnit);
            }
            return;
        }

        // Scout if enemy presence is unknown
        if (!informationManager.isEnemyUnitVisible() && informationManager.getEnemyBuildings().isEmpty()) {
            if (role != UnitRole.SCOUT) {
                createScout(managedUnit);
                squadManager.removeManagedUnit(managedUnit);
            }
            return;
        }

        // Default assignment for units that can fight
        if (managedUnit.canFight()) {
            managedUnit.setRole(UnitRole.FIGHT);
            squadManager.addManagedUnit(managedUnit);
        } else {
            // Fallback for non-combat units not handled above
            createScout(managedUnit);
        }
    }

    public void onUnitComplete(Unit unit) {
        UnitType unitType = unit.getType();
        if (unitType.isBuilding()) {
            if (unitType == UnitType.Zerg_Extractor) {
                workerManager.onExtractorComplete();
            } else {
                ManagedUnit managedUnit = managedUnitLookup.get(unit);
                createBuilding(unit, managedUnit);
            }
            return;
        }

        // Consider case where we are already tracking the unit that morphed
        if (!managedUnitLookup.containsKey(unit)) {
            return;
        }

        ManagedUnit managedUnit = managedUnitLookup.get(unit);
        if (managedUnit.getRole() == UnitRole.MORPH) {
            managedUnit.setRole(UnitRole.IDLE);
        }

        assignManagedUnit(managedUnit);
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

        removeManagedUnit(unit);

        if (type.isBuilding()) {
            createBuilding(unit, managedUnit);
            return;
        }

        createManagedUnit(unit, managedUnit.getRole());

        assignManagedUnit(managedUnit);
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
        ScoutData scoutData = gameState.getScoutData();
        if (role == UnitRole.SCOUT && scoutData.isEnemyBuildingLocationKnown()) {
            squadManager.addManagedUnit(managedUnit);
            scoutManager.removeScout(managedUnit);
        }

        assignManagedUnit(managedUnit);
    }

    private void checkBaseThreats() {
        HashMap<Base, HashSet<Unit>> baseThreats = this.gameState.getBaseToThreatLookup();
        for (Base base: baseThreats.keySet()) {
            if (!baseThreats.get(base).isEmpty()) {
                assignGatherersToDefense(base);
            } else {
                assignDefendersToGather(base);
            }
        }
    }

    private void assignGatherersToDefense(Base base) {
        if (gameState.getGameTime().greaterThan(new Time(5, 0))) {
            return;
        }
        HashSet<ManagedUnit> gatherersAssignedToBase = this.gameState.getGatherersAssignedToBase().get(base);
        if (gatherersAssignedToBase.isEmpty()) {
            return;
        }
        List<Unit> threateningUnits = new ArrayList<>(this.gameState.getBaseToThreatLookup()
                .get(base));



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

    private void createScout(ManagedUnit managedUnit) {
        managedUnit.setRole(UnitRole.SCOUT);
        scoutManager.addScout(managedUnit);
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
