package unit;

import bwapi.Game;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import info.InformationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class UnitManager {

    private Game game;

    // Take a dependency on informationManager for now
    // TODO: Pass GameState here to make decisions for units
    //
    // Should workers go here? Probably, especially if they are pulled for base defense
    private InformationManager informationManager;

    // TODO: refactor
    private HashMap<Unit, ManagedUnit> managedUnitLookup = new HashMap<>();
    private HashSet<ManagedUnit> managedUnits = new HashSet<>();

    public UnitManager(Game game, InformationManager informationManager) {
        this.game = game;
        this.informationManager = informationManager;

        initManagedUnits();
    }

    // TODO: Assign starting drones here as well, then pass to econ manager?
    private void initManagedUnits() {
        for (Unit unit: game.getAllUnits()) {
            if (unit.getPlayer() != game.self()) {
                if (unit.getType() == UnitType.Zerg_Overlord) {
                    ManagedUnit managedUnit = new ManagedUnit(game, unit, UnitRole.SCOUT);
                    managedUnits.add(managedUnit);
                    managedUnitLookup.put(unit, managedUnit);
                }
            }
        }
    }

    public void onFrame() {
        if (game.getFrameCount() % 100 == 0) {
            System.out.printf("managedUnits: [%s]\n", managedUnits);
        }


        for (ManagedUnit managedUnit: managedUnits) {
            if (managedUnit.isCanFight() && managedUnit.getRole() != UnitRole.FIGHT && informationManager.isEnemyLocationKnown()) {
                managedUnit.setRole(UnitRole.FIGHT);
                assignClosestEnemyToManagedUnit(managedUnit);
            } else if (managedUnit.getRole() != UnitRole.SCOUT && !informationManager.isEnemyLocationKnown()) {
                reassignToScout(managedUnit);
            }

            managedUnit.execute();
        }
    }

    public void onUnitComplete(Unit unit) {
        // For now, return early if drone or building
        UnitType unitType = unit.getType();
        if (unitType == UnitType.Zerg_Drone || unitType == UnitType.Zerg_Extractor
                || unitType == UnitType.Zerg_Hatchery || unitType == UnitType.Zerg_Spawning_Pool || unitType == UnitType.Zerg_Hydralisk_Den) {
            return;
        }

        // Assign scouts if we don't know where enemy is
        if (unit.getType() == UnitType.Zerg_Overlord || informationManager.getEnemyBuildings().size() + informationManager.getEnemyUnits().size() == 0) {
            createScout(unit);
        } else {
            ManagedUnit managedFighter = new ManagedUnit(game, unit, UnitRole.FIGHT);
            assignClosestEnemyToManagedUnit(managedFighter);
            managedUnitLookup.put(unit, managedFighter);
            managedUnits.add(managedFighter);
            return;

        }
    }

    public void onUnitDestroy(Unit unit) {
        System.out.printf("onUnitDestory(), id: [%s], unit: [%s], managedUnit: [%s]\n", unit.getID(), unit.getType(), managedUnitLookup.get(unit));
        // Unit Management cleanup
        // TODO(bug): Why aren't these getting cleaned up?
        // Maybe we are assigning as larva?
        ManagedUnit managedUnit = managedUnitLookup.get(unit);
        Boolean isRemoved = managedUnits.remove(managedUnit);
        System.out.printf("onUnitDestroy(), isRemoved: [%s]\n", isRemoved);
        managedUnitLookup.remove(unit);
        return;
    }

    private void assignClosestEnemyToManagedUnit(ManagedUnit managedUnit) {
        List<Unit> enemyUnits = new ArrayList<>();
        if (informationManager.getEnemyUnits().size() > 0) {
            // SetToArray
            // TODO: refactor to util
            informationManager.getEnemyUnits().stream().forEach(enemyUnits::add);
        } else if (informationManager.getEnemyBuildings().size() > 0) {
            informationManager.getEnemyBuildings().stream().forEach(enemyUnits::add);
        }
        managedUnit.assignClosestEnemyAsFightTarget(enemyUnits);
    }

    private TilePosition pollScoutTarget() {
        HashSet<TilePosition> enemyBuildingPositions = informationManager.getEnemyBuildingPositions();
        if (enemyBuildingPositions.size() > 0) {
            for (TilePosition target: enemyBuildingPositions) {
                if (!informationManager.getScoutTargets().contains(target)) {
                    return target;
                }
            }
        }
        HashSet<TilePosition> scoutTargets = informationManager.getScoutTargets();
        for (TilePosition target: scoutTargets) {
            if (!informationManager.getActiveScoutTargets().contains(target)) {
                return target;
            }
        }

        // TODO: Perhaps this is where a null TilePosition has been originating
        return null;
    }

    private void reassignToScout(ManagedUnit managedUnit) {
        managedUnit.setRole(UnitRole.SCOUT);
        TilePosition target = pollScoutTarget();
        informationManager.setActiveScoutTarget(target);
        managedUnit.setMovementTarget(target);
    }

    private void createScout(Unit unit) {
        TilePosition target = pollScoutTarget();
        informationManager.setActiveScoutTarget(target);
        ManagedUnit managedScout = new ManagedUnit(game, unit, UnitRole.SCOUT);
        managedUnitLookup.put(unit, managedScout);
        managedScout.setMovementTarget(target);
        managedUnits.add(managedScout);
        return;
    }
}
