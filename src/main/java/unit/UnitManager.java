package unit;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.GameState;
import info.InformationManager;
import info.ScoutData;
import info.UnitTypeCount;
import lombok.Getter;
import macro.plan.Plan;
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
import java.util.Set;
import java.util.stream.Collectors;

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

    public UnitManager(Game game, InformationManager informationManager, GameState gameState) {
        this.game = game;

        this.gameState = gameState;
        this.factory = new ManagedUnitFactory(game, gameState.getGameMap());

        this.informationManager = informationManager;
        this.workerManager = new WorkerManager(game, gameState);
        this.squadManager = new SquadManager(game, gameState);
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

        // Check every second (24 frames) to minimize overhead
        if (frameCount % 24 == 0) {
            checkAndAssignZerglingScouts();
        }

        buildingManager.onFrame();
        workerManager.onFrame();
        squadManager.updateOverlordSquad();
        squadManager.updateFightSquads();
        if (gameState.isCannonRushed() && !gameState.isCannonRushDefend()) {
            for (Base base : new ArrayList<>(squadManager.getDefenseSquadBases())) {
                List<ManagedUnit> freed = squadManager.disbandDefendSquad(base);
                for (ManagedUnit mu : freed) {
                    mu.getUnit().stop();
                    workerManager.addManagedWorker(mu);
                }
            }
        }
        squadManager.updateDefenseSquads();
        scoutManager.onFrame();

        Set<ManagedUnit> disbandedSquadUnits = squadManager.getDisbandedUnits();
        if (!disbandedSquadUnits.isEmpty()) {
            for (ManagedUnit managedUnit: disbandedSquadUnits) {
                squadManager.removeManagedUnit(managedUnit);
                createScout(managedUnit);
            }
        }

        for (ManagedUnit managedUnit: managedUnits) {
            // Check if unready units can be ready again
            if (!managedUnit.isReady() && game.getFrameCount() >= managedUnit.getUnreadyUntilFrame()) {
                managedUnit.setReady(true);
            }

            UnitRole role = managedUnit.getRole();

            // TODO: Clean this up, this is really just avoiding special handling of scouting units
            switch (role) {
                case GATHER:
                case BUILD:
                case MORPH:
                case LARVA:
                case RETREAT:
                case DEFEND:
                case BUILDING:
                case RALLY:
                case REGROUP:
                case FIGHT:
                    managedUnit.execute();
                    continue;
                default:
                    break;
            }
            UnitType type = managedUnit.getUnitType();
            switch (type) {
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

        // Handle Overlords: once enemy main/building info known, do not reassign to SCOUT
        if (unitType == UnitType.Zerg_Overlord) {
            boolean enemyInfoKnown = gameState.getBaseData().getMainEnemyBase() != null
                    || scoutData.isEnemyBuildingLocationKnown()
                    || informationManager.isEnemyUnitVisible();
            if (enemyInfoKnown) {
                squadManager.addManagedUnit(managedUnit);
                scoutManager.removeScout(managedUnit);
            } else {
                Set<Unit> enemies = new HashSet<>(gameState.getVisibleEnemyUnits());
                if (!scoutData.shouldOverlordsContinueScouting(game.enemy().getRace(), enemies)) {
                    squadManager.addManagedUnit(managedUnit);
                    scoutManager.removeScout(managedUnit);
                } else {
                    createScout(managedUnit);
                }
            }
            return;
        }

        // Scout if enemy presence is unknown
        if (!informationManager.isEnemyUnitVisible() && gameState.getEnemyBuildings().isEmpty()) {
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
                workerManager.onExtractorComplete(unit);
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

        // Cancel any assigned plans when unit dies
        HashMap<Unit, Plan> assignedPlannedItems = gameState.getAssignedPlannedItems();
        Plan assignedPlan = assignedPlannedItems.get(unit);
        if (assignedPlan != null) {
            gameState.cancelPlan(unit, assignedPlan);
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
        if (role == UnitRole.SCOUT) {
            boolean shouldStopScouting = false;

            if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
                Set<Unit> enemies = new HashSet<>(gameState.getVisibleEnemyUnits());
                shouldStopScouting = !scoutData.shouldOverlordsContinueScouting(game.enemy().getRace(), enemies);
            } else if (managedUnit.getUnitType() == UnitType.Zerg_Zergling) {
                shouldStopScouting = scoutManager.endZerglingScout();
            } else {
                shouldStopScouting = scoutData.isEnemyBuildingLocationKnown() || informationManager.isEnemyUnitVisible();
            }

            if (shouldStopScouting) {
                if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
                    managedUnit.setRole(UnitRole.IDLE);
                } else {
                    managedUnit.setRole(UnitRole.RALLY);
                }
                managedUnit.setMovementTargetPosition(null);
                squadManager.addManagedUnit(managedUnit);
                scoutManager.removeScout(managedUnit);
                return;
            }
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

    private void checkAndAssignZerglingScouts() {
        int currentFrame = game.getFrameCount();
        int lastEnemySeenFrame = informationManager.getLastEnemyUnitSeenFrame();

        int scoutsNeeded = scoutManager.needZerglingScouts(currentFrame, lastEnemySeenFrame);
        if (scoutsNeeded == 0) {
            return;
        }

        List<ManagedUnit> idleZerglings = managedUnits.stream()
            .filter(mu -> mu.getUnitType() == UnitType.Zerg_Zergling)
            .filter(mu -> mu.getRole() != UnitRole.SCOUT)
            .collect(Collectors.toList());

        Set<ManagedUnit> disbandedZerglings = squadManager.getDisbandedUnits().stream()
            .filter(mu -> mu.getUnitType() == UnitType.Zerg_Zergling)
            .collect(Collectors.toSet());

        List<ManagedUnit> availableZerglings = new ArrayList<>();
        availableZerglings.addAll(idleZerglings);
        availableZerglings.addAll(disbandedZerglings);

        if (availableZerglings.isEmpty()) {
            return;
        }

        Base enemyMainBase = gameState.getBaseData().getMainEnemyBase();
        if (enemyMainBase == null) {
            return;
        }

        availableZerglings.sort((z1, z2) -> {
            double d1 = z1.getPosition().getDistance(enemyMainBase.getCenter());
            double d2 = z2.getPosition().getDistance(enemyMainBase.getCenter());
            return Double.compare(d1, d2);
        });

        int toAssign = Math.min(scoutsNeeded, availableZerglings.size());
        for (int i = 0; i < toAssign; i++) {
            ManagedUnit zergling = availableZerglings.get(i);
            squadManager.removeManagedUnit(zergling);
            scoutManager.addScout(zergling);
        }
    }

    private void assignGatherersToDefense(Base base) {
        boolean isSCVRush = gameState.getStrategyTracker().isDetectedStrategy("SCVRush");
        if (gameState.getGameTime().greaterThan(new Time(5, 0)) && !isSCVRush) {
            return;
        }
        HashSet<ManagedUnit> gatherersAssignedToBase = this.gameState.getGatherersAssignedToBase().get(base);
        if (gatherersAssignedToBase == null || gatherersAssignedToBase.isEmpty()) {
            Base mainBase = gameState.getBaseData().getMainBase();
            if (mainBase == null || mainBase.equals(base)) {
                return;
            }
            gatherersAssignedToBase = this.gameState.getGatherersAssignedToBase().get(mainBase);
            if (gatherersAssignedToBase == null || gatherersAssignedToBase.isEmpty()) {
                return;
            }
        }
        List<Unit> threateningUnits = new ArrayList<>(this.gameState.getBaseToThreatLookup()
                .get(base));



        List<ManagedUnit> gatherersToReassign = this.squadManager.assignGatherersToDefend(base, gatherersAssignedToBase, threateningUnits);
        for (ManagedUnit managedUnit: gatherersToReassign) {
            this.workerManager.removeManagedWorker(managedUnit);
        }
    }

    private void assignDefendersToGather(Base base) {
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
