package unit.squad;

import bwapi.Color;
import bwapi.Game;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.GameState;
import info.InformationManager;
import org.bk.ass.sim.BWMirrorAgentFactory;
import org.bk.ass.sim.Simulator;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.UnitDistanceComparator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static util.Filter.isHostileBuilding;

public class SquadManager {

    private Game game;
    private GameState gameState;

    private BWMirrorAgentFactory agentFactory;

    private InformationManager informationManager;

    private Squad overlords = new Squad();

    private HashSet<Squad> fightSquads = new HashSet<>();

    private HashMap<Base, Squad> defenseSquads = new HashMap<>();

    public SquadManager(Game game, GameState gameState, InformationManager informationManager) {
        this.game = game;
        this.gameState = gameState;
        this.informationManager = informationManager;
        this.agentFactory = new BWMirrorAgentFactory();
    }

    public void updateOverlordSquad() {
        TilePosition mainBaseLocation = gameState.getBaseData().mainBasePosition();
        if (overlords.getRallyPoint() == null) {
            overlords.setRallyPoint(mainBaseLocation);
        }

        for (ManagedUnit managedUnit: overlords.getMembers()) {
            if (managedUnit.getUnit().getDistance(mainBaseLocation.toPosition()) < 16) {
                managedUnit.setRole(UnitRole.IDLE);
                continue;
            }

            managedUnit.setRole(UnitRole.RETREAT);
            managedUnit.setRetreatTarget(mainBaseLocation);
        }
    }

    public void updateFightSquads() {
        debugPainters();
        removeEmptySquads();

        // Iterate through squads
        // Check if squads are can merge
        mergeSquads();

        // TODO: split behavior (if unit exceeds squad radius)

        //

        for (Squad fightSquad: fightSquads) {
            fightSquad.onFrame();
            rallyOrFight(fightSquad);
        }
    }

    public void updateDefenseSquads() {
        ensureDefenderSquadsHaveTargets();
    }

    /**
     * Determines the number of workers required to defend, assigns them to the defense squad and
     * returns the assigned workers, so they can be removed from the WorkerManager.
     *
     * @param base base to defend
     * @param baseUnits gatherers to assign to defense squad
     * @param hostileUnits units threatening this base
     * @return gatherers that have been assigned to defend
     */
    public List<ManagedUnit> assignGathererDefenders(Base base, HashSet<ManagedUnit> baseUnits, List<Unit> hostileUnits) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        List<ManagedUnit> reassignedGatherers = new ArrayList<>();
        if (baseUnits.size() < 3 || baseUnits.size() - reassignedGatherers.size() < 3) {
            return reassignedGatherers;
        }

        for (ManagedUnit gatherer: baseUnits) {
            Boolean canClear = canDefenseSquadClearThreat(defenseSquad, hostileUnits);
            if (canClear) {
                break;
            }
            defenseSquad.addUnit(gatherer);
            reassignedGatherers.add(gatherer);
        }
        for (ManagedUnit managedUnit: reassignedGatherers) {
            managedUnit.setRole(UnitRole.DEFEND);
            assignDefenderTarget(managedUnit, hostileUnits);
        }

        return reassignedGatherers;
    }

    public List<ManagedUnit> disbandDefendSquad(Base base) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        List<ManagedUnit> reassignedDefenders = new ArrayList<>();
        for (ManagedUnit defender: defenseSquad.getMembers()) {
            reassignedDefenders.add(defender);
        }

        for (ManagedUnit defender: reassignedDefenders) {
            defenseSquad.removeUnit(defender);
        }

        return reassignedDefenders;
    }

    private void ensureDefenseSquad(Base base) {
        if (!defenseSquads.containsKey(base)) {
            Squad squad = new Squad();
            squad.setCenter(base.getCenter());
            squad.setRallyPoint(base.getLocation());
            defenseSquads.put(base, squad);
        }
    }

    private void assignDefenderTarget(ManagedUnit defender, List<Unit> threats) {
        if (defender.getDefendTarget() != null) {
            return;
        }
        threats.sort(new UnitDistanceComparator(defender.getUnit()));
        defender.setDefendTarget(threats.get(0));
    }

    private void ensureDefenderSquadsHaveTargets() {
        for (Base base: defenseSquads.keySet()) {
            ensureDefenseSquad(base);
            Squad squad = defenseSquads.get(base);
            if (defenseSquads.size() == 0) {
                continue;
            }

            // TODO: NPE here where base is lost
            HashSet<Unit> baseThreats = gameState.getBaseToThreatLookup().get(base);
            if (baseThreats == null || baseThreats.size() == 0) {
                continue;
            }
            for (ManagedUnit defender: squad.getMembers()) {
                ensureDefenderHasTarget(defender, baseThreats);
            }
        }
    }

    private void ensureDefenderHasTarget(ManagedUnit defender, HashSet<Unit> baseThreats) {
        if (!defender.isReady()) {
            return;
        }
        if (defender.getDefendTarget() != null) {
            return;
        }
        assignDefenderTarget(defender, baseThreats.stream().collect(Collectors.toList()));
    }

    private void removeEmptySquads() {
        List<Squad> emptySquads = new ArrayList<>();
        for (Squad squad: fightSquads) {
            if (squad.size() == 0) {
                emptySquads.add(squad);
            }
        }

        for (Squad squad: emptySquads) {
            fightSquads.remove(squad);
        }
    }

    private void mergeSquads() {
        // Merge every 50 frames
        if (game.getFrameCount() % 50 != 0) {
            return;
        }

        List<Set<Squad>> toMerge = new ArrayList<>();
        Set<Squad> considered = new HashSet<>();
        for (Squad squad1: fightSquads) {
            for (Squad squad2: fightSquads) {
                if (squad1 == squad2) {
                    continue;
                }
                if (considered.contains(squad1) || considered.contains(squad2)) {
                    continue;
                }
                if (squad1.distance(squad2) < 256) {
                    Set<Squad> mergeSet = new HashSet<>();
                    mergeSet.add(squad1);
                    mergeSet.add(squad2);
                    considered.add(squad1);
                    considered.add(squad2);
                    toMerge.add(mergeSet);
                }
            }
        }

        for (Set<Squad> mergeSet: toMerge) {
            Squad newSquad = new Squad();
            newSquad.setRallyPoint(gameState.getBaseData().mainBasePosition());
            for (Squad mergingSquad: mergeSet) {
                newSquad.merge(mergingSquad);
                fightSquads.remove(mergingSquad);
            }
            fightSquads.add(newSquad);
        }
    }

    private List<Unit> enemyUnitsNearSquad(Squad squad) {
        HashSet<Unit> enemyUnits = informationManager.getVisibleEnemyUnits();

        List<Unit> enemies = new ArrayList<>();

        for (Unit u: enemyUnits) {
            if ( (int) u.getPosition().getDistance(squad.getCenter()) > 256) {
                continue;
            }
            enemies.add(u);
        }

        return enemies;
    }

    private void rallySquad(Squad squad) {
        for (ManagedUnit managedUnit: squad.getMembers()) {
            managedUnit.setRallyPoint(gameState.getBaseData().mainBasePosition());
            managedUnit.setRole(UnitRole.RALLY);
        }
    }

    /**
     * Determines whether a squad should rally or fight.
     * @param squad
     */
    private void rallyOrFight(Squad squad) {
        if (enemyUnitsNearSquad(squad).size() > 0 || squad.size() > 8) {
            simulateFightSquad(squad);
        } else {
            rallySquad(squad);
        }
    }

    /**
     * TODO: Decompose and rename
     *
     * Responsible for running fight simulation
     * @param squad
     */
    private void simulateFightSquad(Squad squad) {
        // Run ASS every 50 frames
        HashSet<ManagedUnit> managedFighters = squad.getMembers();
        if (game.getFrameCount() % 50 == 0 && managedFighters.size() > 0 && informationManager.isEnemyUnitVisible()) {
            HashSet<Unit> enemyUnits = informationManager.getVisibleEnemyUnits();
            HashSet<Unit> enemyBuildings = informationManager.getEnemyBuildings();
            Simulator simulator = new Simulator.Builder().build();

            for (ManagedUnit managedUnit: managedFighters) {
                simulator.addAgentA(agentFactory.of(managedUnit.getUnit()));
            }

            for (Unit enemyUnit: enemyUnits) {
                if (enemyUnit.getType() == UnitType.Unknown) {
                    continue;
                }
                if ( (int) enemyUnit.getPosition().getDistance(squad.getCenter()) > 256) {
                    continue;
                }
                try {
                    simulator.addAgentB(agentFactory.of(enemyUnit));
                } catch (ArithmeticException e) {
                    return;
                }
            }

            for (Unit enemyBuilding: enemyBuildings) {
                if (!isHostileBuilding(enemyBuilding.getType())) {
                    continue;
                }
                if ( (int) enemyBuilding.getPosition().getDistance(squad.getCenter()) > 512) {
                    continue;
                }
                try {
                    simulator.addAgentB(agentFactory.of(enemyBuilding));
                } catch (ArithmeticException e) {
                    return;
                }
            }


            simulator.simulate(150); // Simulate 15 seconds

            if (simulator.getAgentsA().isEmpty()) {
                for (ManagedUnit managedUnit: managedFighters) {
                    managedUnit.setRole(UnitRole.RETREAT);
                    managedUnit.setRetreatTarget(gameState.getBaseData().mainBasePosition());
                }
                return;
            }

            if (!simulator.getAgentsB().isEmpty()) {
                // If less than half of units are left, retreat
                float percentRemaining = (float) simulator.getAgentsA().size() / managedFighters.size();
                if (percentRemaining < 0.20) {
                    for (ManagedUnit managedUnit: managedFighters) {
                        managedUnit.setRole(UnitRole.RETREAT);
                        managedUnit.setRetreatTarget(gameState.getBaseData().mainBasePosition());
                    }
                    return;
                }
            }

            for (ManagedUnit managedUnit: managedFighters) {
                managedUnit.setRole(UnitRole.FIGHT);
            }
            return;
        }
    }

    private boolean canDefenseSquadClearThreat(Squad squad, List<Unit> enemyUnits) {
        HashSet<ManagedUnit> managedDefenders = squad.getMembers();

        Simulator simulator = new Simulator.Builder().build();

        for (ManagedUnit managedUnit: managedDefenders) {
            if (managedUnit.getUnit().getType() == UnitType.Unknown) {
                continue;
            }
            simulator.addAgentA(agentFactory.of(managedUnit.getUnit()));
        }

        for (Unit enemyUnit: enemyUnits) {
            if (enemyUnit.getType() == UnitType.Unknown) {
                continue;
            }
            if ( (int) enemyUnit.getPosition().getDistance(squad.getCenter()) > 256) {
                continue;
            }
            try {
                simulator.addAgentB(agentFactory.of(enemyUnit));
            } catch (ArithmeticException e) {
                return false;
            }
        }

        simulator.simulate(150); // Simulate 15 seconds

        if (simulator.getAgentsB().isEmpty()) {
            return true;
        }

        if (simulator.getAgentsA().isEmpty()) {
            return false;
        }

        float percentRemaining = (float) simulator.getAgentsA().size() / managedDefenders.size();
        if (percentRemaining >= 0.50) {
            return true;
        }

        return false;
    }

    /**
     * Adds a managed unit to the squad manager. Overlords are sorted into the overlord squad; all other units are
     * added to fighr squads.
     * @param managedUnit
     */
    public void addManagedUnit(ManagedUnit managedUnit) {
        if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
            addManagedOverlord(managedUnit);
            return;
        }

        addManagedFighter(managedUnit);
    }

    private void addManagedFighter(ManagedUnit managedUnit) {
        for (Squad squad: fightSquads) {
            if (squad.distance(managedUnit) < 256) {
                squad.addUnit(managedUnit);
                return;
            }
        }

        // No assignment, create new squad
        Squad newSquad = new Squad();
        newSquad.setRallyPoint(gameState.getBaseData().mainBasePosition());
        newSquad.setCenter(managedUnit.getUnit().getPosition());
        newSquad.addUnit(managedUnit);
        fightSquads.add(newSquad);
    }

    private void addManagedOverlord(ManagedUnit overlord) {
        overlords.addUnit(overlord);
    }

    public void removeManagedUnit(ManagedUnit managedUnit) {
        UnitType unitType = managedUnit.getUnitType();
        if (unitType != null && unitType == UnitType.Zerg_Overlord) {
            removeManagedOverlord(managedUnit);
            return;
        }

        removeManagedFigher(managedUnit);
    }

    private void removeManagedFigher(ManagedUnit managedUnit) {
        for (Squad squad: fightSquads) {
            if (squad.containsManagedUnit(managedUnit)) {
                squad.removeUnit(managedUnit);
                return;
            }
        }
        for (Base base: defenseSquads.keySet()) {
            Squad squad = defenseSquads.get(base);
            squad.removeUnit(managedUnit);
        }
    }

    private void removeManagedOverlord(ManagedUnit overlord) {
        overlords.removeUnit(overlord);
    }

    private void debugPainters() {
        for (Squad squad: fightSquads) {
            game.drawCircleMap(squad.getCenter(), 256, Color.White);
        }
        for (Squad squad: defenseSquads.values()) {
            game.drawCircleMap(squad.getCenter(), 256, Color.White);
            game.drawTextMap(squad.getCenter(), String.format("Defenders: %s", squad.size()), Text.White);
        }
    }
}
