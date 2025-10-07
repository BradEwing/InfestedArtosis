package unit.squad;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
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

import static java.lang.Math.min;
import static util.Filter.closestHostileUnit;

public class SquadManager {

    private Game game;
    private GameState gameState;

    private BWMirrorAgentFactory agentFactory;

    private InformationManager informationManager;

    private Squad overlords = new Squad();

    private HashSet<Squad> fightSquads = new HashSet<>();

    private HashMap<Base, Squad> defenseSquads = new HashMap<>();

    private final CombatSimulator defaultCombatSimulator;

    private HashSet<ManagedUnit> disbanded = new HashSet<>();

    public SquadManager(Game game, GameState gameState, InformationManager informationManager) {
        this.game = game;
        this.gameState = gameState;
        // TODO: Information accessed here should come from GameState
        this.informationManager = informationManager;
        this.agentFactory = new BWMirrorAgentFactory();
        this.defaultCombatSimulator = new AssCombatSimulator();
    }

    public void updateFightSquads() {
        debugPainters();
        removeEmptySquads();
        mergeSquads();
        assignOverlordsToSquads();

        Set<Squad> removed = new HashSet<>();
        for (Squad fightSquad: fightSquads) {
            fightSquad.onFrame();
            if (fightSquad.shouldDisband()) {
                disbanded.addAll(disbandSquad(fightSquad));
                removed.add(fightSquad);
            }

            if (fightSquad instanceof MutaliskSquad) {
                MutaliskSquad mutaliskSquad = (MutaliskSquad) fightSquad;
                mutaliskSquad.executeTactics(gameState);


            } else if (fightSquad instanceof ScourgeSquad) {
                ScourgeSquad scourgeSquad = (ScourgeSquad) fightSquad;
                scourgeSquad.executeTactics(gameState);
            } else {
                // Handle other squad types with general logic
                evaluateSquadRole(fightSquad);
            }
        }

        fightSquads.removeAll(removed);
    }

    public void updateOverlordSquad() {
        TilePosition mainBaseLocation = gameState.getBaseData().mainBasePosition();
        if (overlords.getRallyPoint() == null) {
            overlords.setRallyPoint(mainBaseLocation.toPosition());
        }

        for (ManagedUnit managedUnit: overlords.getMembers()) {
            if (managedUnit.getUnit().getDistance(mainBaseLocation.toPosition()) < 16) {
                managedUnit.setRole(UnitRole.IDLE);
                continue;
            }

            managedUnit.setRole(UnitRole.RETREAT);
            managedUnit.setRetreatTarget(mainBaseLocation.toPosition());
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

    /**
     * Disbands a squad and returns all its members for reassignment.
     * Removes the squad from the fight squads collection.
     * Overlords are returned to the overlord squad instead of being added to disbanded list.
     *
     * @param squad Squad to disband
     * @return List of managed units that were in the squad (excluding overlords)
     */
    private List<ManagedUnit> disbandSquad(Squad squad) {
        List<ManagedUnit> members = new ArrayList<>(squad.getMembers());
        List<ManagedUnit> nonOverlordMembers = new ArrayList<>();

        for (ManagedUnit member : members) {
            squad.removeUnit(member);
            
            if (member.getUnitType() == UnitType.Zerg_Overlord) {
                overlords.addUnit(member);
                member.setRole(UnitRole.IDLE);
            } else {
                nonOverlordMembers.add(member);
            }
        }

        return nonOverlordMembers;
    }

    private void ensureDefenseSquad(Base base) {
        if (!defenseSquads.containsKey(base)) {
            Squad squad = new Squad();
            squad.setCenter(base.getCenter());
            squad.setRallyPoint(base.getLocation().toPosition());
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
                if (squad1.getType() != squad2.getType()) {
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
            UnitType type = mergeSet.iterator().next().getType();
            Squad newSquad = newFightSquad(type);
            for (Squad mergingSquad: mergeSet) {
                if (newSquad.getType() == null) {
                    newSquad.setType(mergingSquad.getType());
                }
                newSquad.merge(mergingSquad);
                fightSquads.remove(mergingSquad);
            }
            fightSquads.add(newSquad);
        }
    }

    private List<Unit> enemyUnitsNearSquad(Squad squad) {
        HashSet<Unit> enemyUnits = informationManager.getVisibleEnemyUnits();
        HashSet<Unit> enemyBuildings = informationManager.getEnemyBuildings();

        List<Unit> enemies = new ArrayList<>();

        for (Unit u: enemyUnits) {
            final double d = u.getPosition().getDistance(squad.getCenter());
            if (d > 256.0) {
                continue;
            }
            enemies.add(u);
        }

        for (Unit u: enemyBuildings) {
            final double d = u.getPosition().getDistance(squad.getCenter());
            if (d > 256.0) {
                continue;
            }
            enemies.add(u);
        }

        return enemies;
    }

    private void rallySquad(Squad squad) {
        Position rallyPoint = squad.getType() == UnitType.Zerg_Mutalisk ? getMutaliskRallyPoint(squad) : this.getRallyPoint(squad);
        for (ManagedUnit managedUnit: squad.getMembers()) {
            managedUnit.setRallyPoint(rallyPoint);
            managedUnit.setRole(UnitRole.RALLY);
        }
    }

    private Position getMutaliskRallyPoint(Squad squad) {
        Set<Position> enemyWorkerLocations = gameState.getLastKnownLocationOfEnemyWorkers();
        if (enemyWorkerLocations.isEmpty()) {
            return getRallyPoint(squad);
        }

        Position squadCenter = squad.getCenter();
        Position closestWorkerLocation = null;
        double closestDistance = Double.MAX_VALUE;

        for (Position workerLocation : enemyWorkerLocations) {
            double distance = squadCenter.getDistance(workerLocation);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestWorkerLocation = workerLocation;
            }
        }

        return closestWorkerLocation;
    }

    /**
     * Rallies to the squad closest to the enemy main base, otherwise
     * defaults to the main or natural expansion.
     *
     * Exempts the given squad as a rally candidate.
     *
     * Rallys to the closest squad
     * @return TilePosition to rally squad to
     */
    private Position getRallyPoint(Squad squad) {
        List<Squad> eligibleSquads = fightSquads.stream()
                .filter(s -> s != squad)
                .filter(s -> s.getStatus() == SquadStatus.FIGHT)
                .collect(Collectors.toList());

        final Base enemyMainBase = gameState.getBaseData().getMainEnemyBase();
        if (!eligibleSquads.isEmpty() && enemyMainBase != null) {
            Squad best = null;
            for (Squad s: eligibleSquads) {
                if (best == null) {
                    best = s;
                    continue;
                }
                final double bestDistance = best.getCenter().getDistance(enemyMainBase.getCenter());
                final double candidateDistance = s.getCenter().getDistance(enemyMainBase.getCenter());
                if (candidateDistance < bestDistance) {
                    best = s;
                }
            }

            return best.getCenter().toTilePosition().toPosition();
        }

        return informationManager.getRallyPoint();
    }

    /**
     * Determines whether a squad should continue or change its current role.
     * @param squad Squad to evaluate
     */
    private void evaluateSquadRole(Squad squad) {
        SquadStatus squadStatus = squad.getStatus();
        if (squadStatus == SquadStatus.RETREAT) {
            return;
        }
        final boolean closeThreats = !enemyUnitsNearSquad(squad).isEmpty();

        if (!closeThreats && squadStatus == SquadStatus.REGROUP) {
            return;
        }

        int moveOutThreshold = calculateMoveOutThreshold(squad);
        if (closeThreats || squad.size() > moveOutThreshold) {
            simulateFightSquad(squad);
        } else {
            rallySquad(squad);
        }
    }

    private int calculateMoveOutThreshold(Squad squad) {
        SquadStatus squadStatus = squad.getStatus();
        UnitType type = squad.getType();
        if (type == UnitType.Zerg_Mutalisk) {
            if (gameState.getOpponentRace() == Race.Zerg) {
                return 2;
            }
            return 4;
        } else if (type == UnitType.Zerg_Hydralisk) {
            return 10;
        }
        int staticDefensePenalty = min(informationManager.getEnemyHostileToGroundBuildingsCount(), 5);
        int moveOutThreshold = 5 * (1 + staticDefensePenalty);
        // hysteresis
        if (squadStatus == SquadStatus.FIGHT) {
            moveOutThreshold = moveOutThreshold / 2;
        }

        return moveOutThreshold;
    }

    /**
     * TODO: All simulation should be handled by a CombatSimulator passed to the squad
     * @param squad
     */
    private void simulateFightSquad(Squad squad) {
        // Skip mutalisk squads as they handle their own tactics
        if (squad instanceof MutaliskSquad || squad instanceof ScourgeSquad) {
            return;
        }

        HashSet<ManagedUnit> managedFighters = squad.getMembers();

        // Handle Lurker retreat and contain (unchanged)
        if (squad.getType() == UnitType.Zerg_Lurker) {
            squad.setStatus(SquadStatus.FIGHT);
            for (ManagedUnit managedUnit : managedFighters) {
                managedUnit.setRole(UnitRole.FIGHT);
                assignEnemyTarget(managedUnit, squad);
            }
            return;
        }

        // Handle building targeting when no visible units
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();
        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();

        if (enemyUnits.isEmpty() && !enemyBuildings.isEmpty()) {
            Unit closest = closestHostileUnit(squad.getCenter(), new ArrayList<>(enemyBuildings));
            squad.setStatus(SquadStatus.FIGHT);
            for (ManagedUnit managedUnit : squad.getMembers()) {
                managedUnit.setRole(UnitRole.FIGHT);
                managedUnit.setMovementTargetPosition(closest.getTilePosition());
            }
            return;
        }

        // Use combat simulator for engagement decision
        CombatSimulator.CombatResult result = defaultCombatSimulator.evaluate(squad, gameState);

        switch (result) {
            case RETREAT:
                squad.setStatus(SquadStatus.RETREAT);
                for (ManagedUnit managedUnit : managedFighters) {
                    managedUnit.setRole(UnitRole.RETREAT);
                    Position retreatTarget = managedUnit.getRetreatPosition();
                    managedUnit.setRetreatTarget(retreatTarget);
                }
                break;

            case ENGAGE:
                squad.setStatus(SquadStatus.FIGHT);
                for (ManagedUnit managedUnit : managedFighters) {
                    managedUnit.setRole(UnitRole.FIGHT);
                    assignEnemyTarget(managedUnit, squad);
                }
                break;

            case REGROUP:
                // Handle regroup case if needed
                squad.setStatus(SquadStatus.REGROUP);
                break;
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
     * added to fight squads.
     * @param managedUnit
     */
    public void addManagedUnit(ManagedUnit managedUnit) {
        if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
            addManagedOverlord(managedUnit);
            return;
        }

        addManagedFighter(managedUnit);
    }

    public void onUnitDestroy(Unit unit) {
        for (Squad squad: fightSquads) {
            if (squad.getTarget() == unit) {
                squad.setTarget(null);
            }
        }
    }

    private void addManagedFighter(ManagedUnit managedUnit) {
        UnitType type = managedUnit.getUnitType();
        Squad squad = findCloseSquad(managedUnit, type);
        if (squad == null) {
            squad = newFightSquad(type);
        }
        squad.addUnit(managedUnit);
        simulateFightSquad(squad);
    }

    private Squad findCloseSquad(ManagedUnit managedUnit, UnitType type) {
        for (Squad squad: fightSquads) {
            if (squad.getType() != type) {
                continue;
            }
            if (squad.distance(managedUnit) < 256) {
                return squad;
            }
        }

        return null;
    }

    private Squad newFightSquad(UnitType type) {
        Squad newSquad;

        if (type == UnitType.Zerg_Mutalisk) {
            newSquad = new MutaliskSquad();
        } else if (type == UnitType.Zerg_Scourge) {
            newSquad = new ScourgeSquad();
        } else {
            newSquad = new Squad();
            newSquad.setType(type);
        }

        newSquad.setStatus(SquadStatus.FIGHT);
        newSquad.setRallyPoint(this.getRallyPoint(newSquad));
        fightSquads.add(newSquad);
        return newSquad;
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

        removeManagedFighter(managedUnit);
    }

    private void removeManagedFighter(ManagedUnit managedUnit) {
        for (Squad squad: fightSquads) {
            if (squad.containsManagedUnit(managedUnit)) {
                squad.removeUnit(managedUnit);
                if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
                    overlords.addUnit(managedUnit);
                }
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
        overlord.setRole(UnitRole.IDLE);
    }

    /**
     * Assign target to a fighter and squad.
     *
     * Mutalisk squads should all focus the same unit.
     *
     * Currently, all other unit squads will be assigned the closest enemy unit.
     *
     * @param managedUnit unit that needs a target
     * @param squad squad that passed fight simulation
     */
    private void assignEnemyTarget(ManagedUnit managedUnit, Squad squad) {
        Unit unit = managedUnit.getUnit();
        List<Unit> enemyUnits = new ArrayList<>();
        enemyUnits.addAll(informationManager.getVisibleEnemyUnits());
        enemyUnits.addAll(informationManager.getEnemyBuildings());

        List<Unit> filtered = new ArrayList<>();
        // Attempt to find the closest enemy OUTSIDE fog of war
        for (Unit enemyUnit: enemyUnits) {
            if (unit.getType() == UnitType.Zerg_Lurker && !enemyUnit.isFlying()) {
                filtered.add(enemyUnit);
                continue;
            }
            if (unit.canAttack(enemyUnit) && enemyUnit.isDetected()) {
                filtered.add(enemyUnit);
            }
        }

        if (filtered.isEmpty()) {
            managedUnit.setMovementTargetPosition(informationManager.pollScoutTarget(false));
            return;
        }

        Unit closestEnemy = closestHostileUnit(unit, filtered);
        Unit ft = closestEnemy;
        if (squad.getType() == UnitType.Zerg_Mutalisk) {
            if (squad.getTarget() == null) {
                squad.setTarget(closestEnemy);
            }
            ft = squad.getTarget();
        }
        managedUnit.setFightTarget(ft);
    }

    private void debugPainters() {
        for (Squad squad: fightSquads) {
            game.drawCircleMap(squad.getCenter(), squad.radius(), Color.White);
            game.drawTextMap(squad.getCenter(), String.format("Radius: %d", squad.radius()), Text.White);
        }
        for (Squad squad: defenseSquads.values()) {
            game.drawCircleMap(squad.getCenter(), 256, Color.White);
            game.drawTextMap(squad.getCenter(), String.format("Defenders: %s", squad.size()), Text.White);
        }
    }

    /**
     * Retrieves the largest fight squad or returns null if no fight squads exist.
     * @return Squad OR null
     */
    public Squad largestSquad() {
        if (fightSquads.isEmpty()) {
            return null;
        }
        List<Squad> sorted = fightSquads.stream().sorted().collect(Collectors.toList());
        return sorted.get(sorted.size()-1);
    }

    public Set<ManagedUnit> getDisbandedUnits() {
        return disbanded;
    }

    /**
     * Assigns overlords to Hydralisk and Mutalisk squads when overlord speed is researched.
     * Prioritizes largest squads first and assigns one overlord per squad.
     * Returns overlords to the main squad if they're the only unit left.
     */
    private void assignOverlordsToSquads() {
        if (!gameState.getTechProgression().isOverlordSpeed()) {
            return;
        }

        returnLoneOverlords();

        List<ManagedUnit> availableOverlords = new ArrayList<>(overlords.getMembers());
        if (availableOverlords.isEmpty()) {
            return;
        }

        List<Squad> targetSquads = getHydraliskAndMutaliskSquads();
        if (targetSquads.isEmpty()) {
            return;
        }

        for (Squad squad : targetSquads) {
            if (availableOverlords.isEmpty()) {
                break;
            }

            if (squadHasOverlord(squad)) {
                continue;
            }

            ManagedUnit overlord = availableOverlords.remove(0);
            overlords.removeUnit(overlord);
            squad.addUnit(overlord);
            overlord.setRole(UnitRole.FIGHT);
        }
    }

    /**
     * Returns overlords to the main overlord squad if they are the only unit type in the squad.
     */
    private void returnLoneOverlords() {
        List<Squad> squadsToRemove = new ArrayList<>();
        
        for (Squad squad : fightSquads) {
            boolean allOverlords = true;
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getUnitType() != UnitType.Zerg_Overlord) {
                    allOverlords = false;
                    break;
                }
            }
            
             if (allOverlords && squad.size() > 0) {
                 List<ManagedUnit> overlordsToRemove = new ArrayList<>(squad.getMembers());
                 for (ManagedUnit overlord : overlordsToRemove) {
                     squad.removeUnit(overlord);
                     overlords.addUnit(overlord);
                     overlord.setRallyPoint(informationManager.getRallyPoint());
                     overlord.setRole(UnitRole.RETREAT);
                 }
                 squadsToRemove.add(squad);
             }
        }
        fightSquads.removeAll(squadsToRemove);
    }

    /**
     * Returns a list of Hydralisk and Mutalisk squads, sorted by size (largest first).
     */
    private List<Squad> getHydraliskAndMutaliskSquads() {
        return fightSquads.stream()
            .filter(squad -> squad.getType() == UnitType.Zerg_Hydralisk || 
                           squad.getType() == UnitType.Zerg_Mutalisk)
            .sorted((s1, s2) -> Integer.compare(s2.size(), s1.size())) // Sort descending by size
            .collect(Collectors.toList());
    }

    /**
     * Checks if a squad already has an overlord assigned to it.
     */
    private boolean squadHasOverlord(Squad squad) {
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getUnitType() == UnitType.Zerg_Overlord) {
                return true;
            }
        }
        return false;
    }
}


