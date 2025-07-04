package unit.squad;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
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

    // Update the updateFightSquads method in SquadManager.java
    public void updateFightSquads() {
        debugPainters();
        removeEmptySquads();

        // Iterate through squads
        // Check if squads are can merge
        mergeSquads();

        // TODO: split behavior (if unit exceeds squad radius)

        for (Squad fightSquad: fightSquads) {
            fightSquad.onFrame();

            // Check if mutalisk squads should transition from rally to fight
            checkMutaliskRallyTransition(fightSquad);

            evaluateSquadRole(fightSquad);
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
            Squad newSquad = new Squad();
            newSquad.setStatus(SquadStatus.FIGHT);
            newSquad.setRallyPoint(this.getRallyPoint(newSquad));
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
        final boolean closeThreats = enemyUnitsNearSquad(squad).size() > 0;

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
        }
        int staticDefensePenalty = min(informationManager.getEnemyHostileToGroundBuildingsCount(), 5);
        int moveOutThreshold = 5 * (1 + staticDefensePenalty);
        // hysteresis
        if (squadStatus == SquadStatus.FIGHT) {
            moveOutThreshold = moveOutThreshold / 2;
        }

        return moveOutThreshold;
    }

    private void handleMutaliskSquad(Squad squad) {
        if (squad.getType() != UnitType.Zerg_Mutalisk) {
            return;
        }

        if (squad.getMembers().size() < 2) {
            squad.setStatus(SquadStatus.RALLY);
            Position homeBase = getRallyPoint(squad);

            for (ManagedUnit mutalisk : squad.getMembers()) {
                mutalisk.setRole(UnitRole.RALLY);
                mutalisk.setReady(true);
                mutalisk.setRallyPoint(homeBase);
            }
            return;
        }

        HashSet<Unit> enemyUnits = informationManager.getVisibleEnemyUnits();
        HashSet<Unit> enemyBuildings = informationManager.getEnemyBuildings();

        // Get aerial static defense coverage
        Set<Position> staticDefenseCoverage = gameState.getAerielStaticDefenseCoverage();

        // Get all enemy units and buildings within reasonable range
        List<Unit> allEnemies = new ArrayList<>();
        for (Unit enemy : enemyUnits) {
            if (enemy.isDetected() && !enemy.isInvincible()) {
                allEnemies.add(enemy);
            }
        }
        for (Unit building : enemyBuildings) {
            if (building.isDetected() && !building.isInvincible()) {
                allEnemies.add(building);
            }
        }

        if (allEnemies.isEmpty()) {
            // No enemies visible, continue scouting
            rallySquad(squad);
            return;
        }

        Position squadCenter = squad.getCenter();
        Position retreatVector = calculateRetreatVector(squadCenter, allEnemies, staticDefenseCoverage);

        Unit priorityTarget = findPriorityTarget(squad, allEnemies, staticDefenseCoverage);

        boolean shouldEngage = evaluateMutaliskEngagement(squad, allEnemies) && priorityTarget != null;

        if (!shouldEngage) {
            squad.setStatus(SquadStatus.RETREAT);
            for (ManagedUnit mutalisk : squad.getMembers()) {
                mutalisk.setRole(UnitRole.RETREAT);
                mutalisk.setReady(true);
                mutalisk.setRetreatTarget(retreatVector);
            }
            return;
        }

        if (canAttackTargetFromSafePosition(priorityTarget, staticDefenseCoverage)) {
            Position safeAttackPos = findSafeAttackPosition(priorityTarget, staticDefenseCoverage);
            if (safeAttackPos != null) {
                squad.setStatus(SquadStatus.RALLY);
                squad.setTarget(priorityTarget);

                for (ManagedUnit mutalisk : squad.getMembers()) {
                    mutalisk.setRole(UnitRole.RALLY);
                    mutalisk.setReady(true);
                    mutalisk.setRallyPoint(safeAttackPos);
                    mutalisk.setFightTarget(priorityTarget);
                }
                return;
            }
        }

        // Default fight behavior if no safe repositioning needed
        squad.setStatus(SquadStatus.FIGHT);
        squad.setTarget(priorityTarget);

        for (ManagedUnit mutalisk : squad.getMembers()) {
            mutalisk.setRole(UnitRole.FIGHT);
            mutalisk.setReady(true);
            mutalisk.setFightTarget(priorityTarget);

            Position individualRetreat = calculateIndividualRetreat(mutalisk.getUnit(), allEnemies, staticDefenseCoverage);
            mutalisk.setRetreatTarget(individualRetreat);
        }
    }

    private Position calculateRetreatVector(Position squadCenter, List<Unit> enemies, Set<Position> staticDefenseCoverage) {
        if (enemies.isEmpty()) {
            return squadCenter;
        }

        // Calculate weighted vector away from enemies
        double totalDx = 0;
        double totalDy = 0;
        double totalWeight = 0;

        for (Unit enemy : enemies) {
            Position enemyPos = enemy.getPosition();
            double distance = squadCenter.getDistance(enemyPos);

            // Weight more dangerous units higher
            double weight = 1.0;
            if (isAntiAir(enemy)) {
                weight = 3.0;
            } else if (enemy.getType().isBuilding()) {
                weight = 0.5;
            }

            // Inverse square law for influence
            if (distance > 0) {
                weight = weight / (distance * distance / 10000); // Normalize by 100^2

                double dx = squadCenter.getX() - enemyPos.getX();
                double dy = squadCenter.getY() - enemyPos.getY();

                totalDx += dx * weight;
                totalDy += dy * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight == 0) {
            return squadCenter;
        }

        // Normalize and scale the retreat vector
        double normalizedDx = totalDx / totalWeight;
        double normalizedDy = totalDy / totalWeight;

        double length = Math.sqrt(normalizedDx * normalizedDx + normalizedDy * normalizedDy);
        if (length > 0) {
            normalizedDx = (normalizedDx / length) * 256; // Retreat 256 pixels
            normalizedDy = (normalizedDy / length) * 256;
        }

        int retreatX = squadCenter.getX() + (int)normalizedDx;
        int retreatY = squadCenter.getY() + (int)normalizedDy;

        // Ensure retreat position is within map bounds
        retreatX = Math.max(0, Math.min(retreatX, game.mapWidth() * 32 - 1));
        retreatY = Math.max(0, Math.min(retreatY, game.mapHeight() * 32 - 1));

        Position retreatPos = new Position(retreatX, retreatY);

        // If retreat position is in static defense coverage, try to find alternative
        if (isPositionInStaticDefenseCoverage(retreatPos, staticDefenseCoverage)) {
            Position alternativeRetreat = findSafeRetreatPosition(squadCenter, staticDefenseCoverage);
            if (alternativeRetreat != null) {
                retreatPos = alternativeRetreat;
            }
        }

        return retreatPos;
    }

    private Position calculateIndividualRetreat(Unit mutalisk, List<Unit> enemies, Set<Position> staticDefenseCoverage) {
        Position mutaliskPos = mutalisk.getPosition();

        // Find nearest threatening enemy
        Unit nearestThreat = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Unit enemy : enemies) {
            if (isAntiAir(enemy)) {
                double distance = mutaliskPos.getDistance(enemy.getPosition());
                if (distance < nearestDistance) {
                    nearestThreat = enemy;
                    nearestDistance = distance;
                }
            }
        }

        if (nearestThreat == null) {
            // No immediate threats, use squad retreat vector
            return calculateRetreatVector(mutaliskPos, enemies, staticDefenseCoverage);
        }

        // Calculate retreat away from nearest threat
        Position threatPos = nearestThreat.getPosition();
        double dx = mutaliskPos.getX() - threatPos.getX();
        double dy = mutaliskPos.getY() - threatPos.getY();

        double length = Math.sqrt(dx * dx + dy * dy);
        if (length > 0) {
            dx = (dx / length) * 192; // Individual retreat distance
            dy = (dy / length) * 192;
        }

        int retreatX = mutaliskPos.getX() + (int)dx;
        int retreatY = mutaliskPos.getY() + (int)dy;

        // Ensure within bounds
        retreatX = Math.max(0, Math.min(retreatX, game.mapWidth() * 32 - 1));
        retreatY = Math.max(0, Math.min(retreatY, game.mapHeight() * 32 - 1));

        Position retreatPos = new Position(retreatX, retreatY);

        // If retreat position is in static defense coverage, try to find alternative
        if (isPositionInStaticDefenseCoverage(retreatPos, staticDefenseCoverage)) {
            Position alternativeRetreat = findSafeRetreatPosition(mutaliskPos, staticDefenseCoverage);
            if (alternativeRetreat != null) {
                retreatPos = alternativeRetreat;
            }
        }

        return retreatPos;
    }

    private List<Unit> findWorkers(List<Unit> enemies) {
        List<Unit> workers = new ArrayList<>();

        for (Unit unit : enemies) {
            UnitType type = unit.getType();
            if (type == UnitType.Protoss_Probe ||
                    type == UnitType.Terran_SCV ||
                    type == UnitType.Zerg_Drone) {
                workers.add(unit);
            }
        }

        return workers;
    }

    private boolean isPositionInStaticDefenseCoverage(Position pos, Set<Position> staticDefenseCoverage) {
        // Check if position is within static defense coverage
        // Since coverage positions are discretized (every 8 pixels), check nearby positions
        for (Position coveredPos : staticDefenseCoverage) {
            if (pos.getDistance(coveredPos) < 16) { // Within 16 pixels tolerance
                return true;
            }
        }
        return false;
    }

    private Position findSafeRetreatPosition(Position currentPos, Set<Position> staticDefenseCoverage) {
        // Try to find a safe retreat position by checking positions in a spiral pattern
        int maxRadius = 320; // Maximum search radius
        int step = 32; // Step size for position checking

        for (int radius = step; radius <= maxRadius; radius += step) {
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle);
                int x = currentPos.getX() + (int)(radius * Math.cos(radians));
                int y = currentPos.getY() + (int)(radius * Math.sin(radians));

                // Ensure within map bounds
                x = Math.max(0, Math.min(x, game.mapWidth() * 32 - 1));
                y = Math.max(0, Math.min(y, game.mapHeight() * 32 - 1));

                Position candidatePos = new Position(x, y);

                if (!isPositionInStaticDefenseCoverage(candidatePos, staticDefenseCoverage)) {
                    return candidatePos;
                }
            }
        }

        return null;
    }

    private List<Unit> findCloseAntiAirThreats(Squad squad, List<Unit> enemies) {
        List<Unit> threats = new ArrayList<>();
        Position squadCenter = squad.getCenter();

        for (Unit enemy : enemies) {
            // Check if unit can attack air and is within engagement range
            if (isAntiAir(enemy) && squadCenter.getDistance(enemy.getPosition()) < 256) {
                threats.add(enemy);
            }
        }

        return threats;
    }

    private Unit findPriorityTarget(Squad squad, List<Unit> enemies, Set<Position> staticDefenseCoverage) {
        Position squadCenter = squad.getCenter();

        // Separate enemies by whether they're in static defense coverage
        List<Unit> safeTargets = new ArrayList<>();
        List<Unit> dangerousTargets = new ArrayList<>();

        for (Unit enemy : enemies) {
            if (isPositionInStaticDefenseCoverage(enemy.getPosition(), staticDefenseCoverage)) {
                dangerousTargets.add(enemy);
            } else {
                safeTargets.add(enemy);
            }
        }

        boolean safeTargetsOnlyBuildings = safeTargets.stream().allMatch(unit -> unit.getType().isBuilding());
        List<Unit> preferredTargets = (safeTargets.isEmpty() || safeTargetsOnlyBuildings) ? dangerousTargets : safeTargets;

        List<Unit> workers = findWorkers(preferredTargets);
        if (!workers.isEmpty()) {
            return getClosestUnit(squadCenter, workers);
        }

        List<Unit> closeAntiAirThreats = findCloseAntiAirThreats(squad, preferredTargets);
        if (!closeAntiAirThreats.isEmpty()) {
            return getClosestUnit(squadCenter, closeAntiAirThreats);
        }

        List<Unit> strayUnits = findStrayUnits(preferredTargets);
        if (!strayUnits.isEmpty()) {
            return getClosestUnit(squadCenter, strayUnits);
        }

        List<Unit> staticDefense = findStaticDefense(preferredTargets);
        if (!staticDefense.isEmpty()) {
            return getClosestUnit(squadCenter, staticDefense);
        }

        return getClosestUnit(squadCenter, preferredTargets);
    }

    private List<Unit> findStrayUnits(List<Unit> enemies) {
        List<Unit> strayUnits = new ArrayList<>();

        for (Unit unit : enemies) {
            if (unit.getType().isBuilding()) {
                continue;
            }

            // Find distance to nearest ally
            double nearestAllyDistance = Double.MAX_VALUE;
            for (Unit other : enemies) {
                if (other != unit && !other.getType().isBuilding()) {
                    double distance = unit.getDistance(other);
                    if (distance < nearestAllyDistance) {
                        nearestAllyDistance = distance;
                    }
                }
            }

            // Units more than 200 pixels from nearest ally are stray
            if (nearestAllyDistance > 64) {
                strayUnits.add(unit);
            }
        }

        return strayUnits;
    }

    private List<Unit> findStaticDefense(List<Unit> enemies) {
        List<Unit> staticDefense = new ArrayList<>();

        for (Unit unit : enemies) {
            UnitType type = unit.getType();
            if (type == UnitType.Terran_Missile_Turret ||
                    type == UnitType.Terran_Bunker ||
                    type == UnitType.Protoss_Photon_Cannon) {
                staticDefense.add(unit);
            }
        }

        return staticDefense;
    }

    private Unit getClosestUnit(Position from, List<Unit> units) {
        Unit closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Unit unit : units) {
            double distance = from.getDistance(unit.getPosition());
            if (distance < closestDistance) {
                closest = unit;
                closestDistance = distance;
            }
        }

        return closest;
    }

    private boolean evaluateMutaliskEngagement(Squad squad, List<Unit> enemies) {
        // Count anti-air threats
        int antiAirThreats = 0;
        int antiAirDps = 0;

        for (Unit enemy : enemies) {
            if (isAntiAir(enemy) && squad.getCenter().getDistance(enemy.getPosition()) < 160) {
                antiAirThreats++;
                antiAirDps += getAntiAirDps(enemy);
            }
        }

        int squadSize = squad.size();
        int mutaliskHp = squadSize * 120;
        int expectedDamagePerSecond = antiAirDps;

        if (expectedDamagePerSecond * 5 > mutaliskHp * 0.5) {
            return false;
        }

        // Don't engage if heavily outnumbered by anti-air
        if (antiAirThreats > squadSize * 1.5) {
            return false;
        }

        return true;
    }

    private boolean isAntiAir(Unit unit) {
        UnitType type = unit.getType();

        // Check if unit can attack air
        if (type.airWeapon() != WeaponType.None) {
            return true;
        }

        // Special cases
        if (type == UnitType.Terran_Bunker && !unit.getLoadedUnits().isEmpty()) {
            return true; // Assume bunker has marines
        }

        return false;
    }

    private int getAntiAirDps(Unit unit) {
        UnitType type = unit.getType();
        WeaponType weapon = type.airWeapon();

        if (weapon == WeaponType.None) {
            return 0;
        }

        // Calculate DPS
        int damage = weapon.damageAmount();
        int cooldown = weapon.damageCooldown();

        if (cooldown == 0) {
            return 0;
        }

        // Account for upgrades if needed
        int dps = (damage * 24) / cooldown;

        return dps;
    }

    /**
     * TODO: Decompose and rename
     *
     * Responsible for running fight simulation
     * @param squad
     */
    private void simulateFightSquad(Squad squad) {
        if (squad.getType() == UnitType.Zerg_Mutalisk) {
            handleMutaliskSquad(squad);
            return;
        }

        // Run ASS every 50 frames
        HashSet<ManagedUnit> managedFighters = squad.getMembers();

        // TODO: Handle Lurker retreat and contain
        if (squad.getType() == UnitType.Zerg_Lurker) {
            squad.setStatus(SquadStatus.FIGHT);
            for (ManagedUnit managedUnit: managedFighters) {
                managedUnit.setRole(UnitRole.FIGHT);
                assignEnemyTarget(managedUnit, squad);
            }
        }

        HashSet<Unit> enemyBuildings = informationManager.getEnemyBuildings();
        Unit closest = closestHostileUnit(squad.getCenter(), new ArrayList<>(enemyBuildings));

        if (!informationManager.isEnemyUnitVisible() && !enemyBuildings.isEmpty()) {
            squad.setStatus(SquadStatus.FIGHT);
            for (ManagedUnit managedUnit: squad.getMembers()) {
                managedUnit.setRole(UnitRole.FIGHT);
                managedUnit.setMovementTargetPosition(closest.getTilePosition());
            }
        }

        HashSet<Unit> enemyUnits = informationManager.getVisibleEnemyUnits();
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
            if (enemyUnit.isBeingConstructed() || enemyUnit.isMorphing()) {
                continue;
            }
            if (enemyUnit.getType().isWorker()) {
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
            if (enemyBuilding.isMorphing() || enemyBuilding.isBeingConstructed()) {
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
            squad.setStatus(SquadStatus.RETREAT);
            for (ManagedUnit managedUnit: managedFighters) {
                managedUnit.setRole(UnitRole.RETREAT);
                Position retreatTarget = this.getRallyPoint(squad);
                managedUnit.setRetreatTarget(retreatTarget);
            }
            return;
        }

        if (!simulator.getAgentsB().isEmpty()) {
            // If less than half of units are left, retreat
            float percentRemaining = (float) simulator.getAgentsA().size() / managedFighters.size();
            if (percentRemaining < 0.40) {
                squad.setStatus(SquadStatus.RETREAT);
                for (ManagedUnit managedUnit: managedFighters) {
                    managedUnit.setRole(UnitRole.RETREAT);
                    Position retreatTarget = managedUnit.getRetreatPosition();
                    managedUnit.setRetreatTarget(retreatTarget);
                }
                return;
            }
        }

        squad.setStatus(SquadStatus.FIGHT);
        for (ManagedUnit managedUnit: managedFighters) {
            managedUnit.setRole(UnitRole.FIGHT);
            assignEnemyTarget(managedUnit, squad);
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
        Squad newSquad = new Squad();
        newSquad.setStatus(SquadStatus.FIGHT);
        newSquad.setRallyPoint(this.getRallyPoint(newSquad));
        newSquad.setType(type);
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

    // Add rally transition check method
    private void checkMutaliskRallyTransition(Squad squad) {
        boolean isRallyOrRetreat = squad.getStatus() == SquadStatus.RALLY || squad.getStatus() == SquadStatus.RETREAT;
        if (squad.getType() != UnitType.Zerg_Mutalisk || isRallyOrRetreat) {
            return;
        }

        int mutaliskCount = squad.getMembers().size();
        int mutaliskAtRally = 0;

        for (ManagedUnit mutalisk : squad.getMembers()) {
            if (mutalisk.getRallyPoint() != null) {
                double distanceToRally = mutalisk.getUnit().getPosition().getDistance(mutalisk.getRallyPoint());
                if (distanceToRally < 64) {
                    mutaliskAtRally++;
                }
            }
        }

        if (mutaliskCount > 0 && (double)mutaliskAtRally / mutaliskCount >= 0.75) {
            squad.setStatus(SquadStatus.FIGHT);

            for (ManagedUnit mutalisk : squad.getMembers()) {
                mutalisk.setRole(UnitRole.FIGHT);
                mutalisk.setReady(true);
            }
        }
    }

    /**
     * Calculates a score for a position based on distance from static defenses and terrain type.
     * Higher score is better.
     */
    private double calculatePositionScore(Position pos, Set<Position> staticDefenseCoverage) {
        double score = 0;

        double minDistanceToStaticDefense = Double.MAX_VALUE;
        for (Position defensePos : staticDefenseCoverage) {
            double distance = pos.getDistance(defensePos);
            if (distance < minDistanceToStaticDefense) {
                minDistanceToStaticDefense = distance;
            }
        }

        if (minDistanceToStaticDefense != Double.MAX_VALUE) {
            score += Math.min(100, minDistanceToStaticDefense / 3.2);
        } else {
            score += 100;
        }

        if (!game.isBuildable(pos.toTilePosition())) {
            score += 50;
        }

        return score;
    }

    /**
     * Finds a safe attack position for mutalisks to attack a target from outside static defense coverage.
     * Prefers positions that are far from static defenses and on unbuildable terrain.
     */
    private Position findSafeAttackPosition(Unit target, Set<Position> staticDefenseCoverage) {
        Position targetPos = target.getPosition();
        int mutaRange = UnitType.Zerg_Mutalisk.airWeapon().maxRange();

        List<Position> candidatePositions = new ArrayList<>();

        int searchRadius = mutaRange;

        for (int angle = 0; angle < 360; angle += 15) {
            double radians = Math.toRadians(angle);
            int x = targetPos.getX() + (int)(searchRadius * Math.cos(radians));
            int y = targetPos.getY() + (int)(searchRadius * Math.sin(radians));

            x = Math.max(0, Math.min(x, game.mapWidth() * 32 - 1));
            y = Math.max(0, Math.min(y, game.mapHeight() * 32 - 1));

            Position candidatePos = new Position(x, y);

            if (!isPositionInStaticDefenseCoverage(candidatePos, staticDefenseCoverage)) {
                candidatePositions.add(candidatePos);
            }
        }

        if (candidatePositions.isEmpty()) {
            return null;
        }

        candidatePositions.sort((pos1, pos2) -> {
            double score1 = calculatePositionScore(pos1, staticDefenseCoverage);
            double score2 = calculatePositionScore(pos2, staticDefenseCoverage);
            return Double.compare(score2, score1);
        });

        return candidatePositions.get(0);
    }

    /**
     * Checks if a target is within static defense coverage and can be attacked from outside that coverage.
     */
    private boolean canAttackTargetFromSafePosition(Unit target, Set<Position> staticDefenseCoverage) {
        if (!isPositionInStaticDefenseCoverage(target.getPosition(), staticDefenseCoverage)) {
            return false;
        }

        Position safePos = findSafeAttackPosition(target, staticDefenseCoverage);
        return safePos != null;
    }
}


