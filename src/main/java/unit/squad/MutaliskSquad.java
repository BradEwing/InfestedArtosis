package unit.squad;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Specialized squad implementation for Mutalisk units.
 * Handles air unit specific tactics including harassment, retreat calculations,
 * and static defense avoidance.
 */
public class MutaliskSquad extends Squad {

    private final CombatSimulator combatSimulator;

    public MutaliskSquad() {
        super();
        this.combatSimulator = new MutaliskCombatSimulator();
        this.setType(UnitType.Zerg_Mutalisk);
    }

    /**
     * Executes mutalisk-specific squad behavior including target selection,
     * retreat calculations, and engagement decisions.
     */
    public void executeTactics(GameState gameState) {
        if (getMembers().size() < 3) {
            setStatus(SquadStatus.RALLY);
            rallyToSafePosition(gameState);
            return;
        }

        Set<Unit> enemyUnits = gameState.getVisibleEnemyUnits();
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();

        List<Unit> allEnemies = new ArrayList<>();
        allEnemies.addAll(enemyUnits);
        allEnemies.addAll(enemyBuildings);

        if (allEnemies.isEmpty()) {
            setStatus(SquadStatus.RALLY);
            rallyToHarassmentPosition(gameState);
            return;
        }

        Set<Position> staticDefenseCoverage = gameState.getAerielStaticDefenseCoverage();
        Position squadCenter = getCenter();

        // Evaluate engagement
        CombatSimulator.CombatResult combatResult = combatSimulator.evaluate(this, gameState);

        if (combatResult == CombatSimulator.CombatResult.RETREAT) {
            setStatus(SquadStatus.RETREAT);
            executeRetreat(gameState, allEnemies, staticDefenseCoverage);
            return;
        }

        // Find priority target
        Unit priorityTarget = findPriorityTarget(allEnemies, staticDefenseCoverage);

        if (priorityTarget != null && canAttackTargetFromSafePosition(priorityTarget, staticDefenseCoverage)) {
            Position safeAttackPos = findSafeAttackPosition(priorityTarget, staticDefenseCoverage);
            if (safeAttackPos != null) {
                setStatus(SquadStatus.RALLY);
                setTarget(priorityTarget);
                rallyToPosition(safeAttackPos, priorityTarget);
                return;
            }
        }

        // Default fight behavior
        setStatus(SquadStatus.FIGHT);
        if (priorityTarget != null) {
            setTarget(priorityTarget);
        }

        for (ManagedUnit mutalisk : getMembers()) {
            mutalisk.setRole(UnitRole.FIGHT);
            mutalisk.setReady(true);
            if (priorityTarget != null) {
                mutalisk.setFightTarget(priorityTarget);
            }

            Position individualRetreat = calculateIndividualRetreat(mutalisk.getUnit(), allEnemies, staticDefenseCoverage);
            mutalisk.setRetreatTarget(individualRetreat);
        }
    }

    /**
     * Executes retreat behavior for the entire squad.
     */
    private void executeRetreat(GameState gameState, List<Unit> allEnemies, Set<Position> staticDefenseCoverage) {
        Position retreatVector = calculateRetreatVector(getCenter(), allEnemies, staticDefenseCoverage, gameState);

        for (ManagedUnit mutalisk : getMembers()) {
            mutalisk.setRole(UnitRole.RETREAT);
            mutalisk.setReady(true);
            mutalisk.setRetreatTarget(retreatVector);
        }
    }

    /**
     * Rally squad to harassment position (near enemy workers).
     */
    private void rallyToHarassmentPosition(GameState gameState) {
        Set<Position> enemyWorkerLocations = gameState.getLastKnownLocationOfEnemyWorkers();
        Position rallyPoint;

        if (enemyWorkerLocations.isEmpty()) {
            rallyPoint = gameState.getSquadRallyPoint();
        } else {
            rallyPoint = findClosestWorkerLocation(enemyWorkerLocations);
        }

        rallyToPosition(rallyPoint, null);
    }

    /**
     * Rally squad to safe position away from threats.
     */
    private void rallyToSafePosition(GameState gameState) {
        Position homeBase = gameState.getSquadRallyPoint();
        rallyToPosition(homeBase, null);
    }

    /**
     * Rally all mutalisks to specified position.
     */
    private void rallyToPosition(Position position, Unit target) {
        for (ManagedUnit mutalisk : getMembers()) {
            mutalisk.setRole(UnitRole.RALLY);
            mutalisk.setReady(true);
            mutalisk.setRallyPoint(position);
            if (target != null) {
                mutalisk.setFightTarget(target);
            }
        }
    }

    /**
     * Finds the closest enemy worker location to the squad.
     */
    private Position findClosestWorkerLocation(Set<Position> workerLocations) {
        Position squadCenter = getCenter();
        Position closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Position workerLocation : workerLocations) {
            double distance = squadCenter.getDistance(workerLocation);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = workerLocation;
            }
        }

        return closest != null ? closest : squadCenter;
    }

    /**
     * Calculates retreat vector away from enemies and static defenses.
     */
    private Position calculateRetreatVector(Position squadCenter, List<Unit> enemies,
                                            Set<Position> staticDefenseCoverage, GameState gameState) {
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
                weight = weight / (distance * distance / 10000);

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
            normalizedDx = (normalizedDx / length) * 256;
            normalizedDy = (normalizedDy / length) * 256;
        }

        int retreatX = squadCenter.getX() + (int)normalizedDx;
        int retreatY = squadCenter.getY() + (int)normalizedDy;

        // Ensure retreat position is within map bounds
        retreatX = Math.max(0, Math.min(retreatX, gameState.getGame().mapWidth() * 32 - 1));
        retreatY = Math.max(0, Math.min(retreatY, gameState.getGame().mapHeight() * 32 - 1));

        Position retreatPos = new Position(retreatX, retreatY);

        // If retreat position is in static defense coverage, try to find alternative
        if (isPositionInStaticDefenseCoverage(retreatPos, staticDefenseCoverage)) {
            Position alternativeRetreat = findSafeRetreatPosition(squadCenter, staticDefenseCoverage, gameState);
            if (alternativeRetreat != null) {
                retreatPos = alternativeRetreat;
            }
        }

        return retreatPos;
    }

    /**
     * Calculates individual retreat position for a single mutalisk.
     */
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
            return mutaliskPos;
        }

        // Calculate retreat away from nearest threat
        Position threatPos = nearestThreat.getPosition();
        double dx = mutaliskPos.getX() - threatPos.getX();
        double dy = mutaliskPos.getY() - threatPos.getY();

        double length = Math.sqrt(dx * dx + dy * dy);
        if (length > 0) {
            dx = (dx / length) * 192;
            dy = (dy / length) * 192;
        }

        int retreatX = mutaliskPos.getX() + (int)dx;
        int retreatY = mutaliskPos.getY() + (int)dy;

        // Ensure within bounds (would need gameState for map dimensions)
        Position retreatPos = new Position(retreatX, retreatY);

        return retreatPos;
    }

    // Helper methods (simplified versions of the original SquadManager methods)

    private Unit findPriorityTarget(List<Unit> enemies, Set<Position> staticDefenseCoverage) {
        Position squadCenter = getCenter();

        // Separate enemies by safety
        List<Unit> safeTargets = new ArrayList<>();
        List<Unit> dangerousTargets = new ArrayList<>();

        for (Unit enemy : enemies) {
            if (isPositionInStaticDefenseCoverage(enemy.getPosition(), staticDefenseCoverage)) {
                dangerousTargets.add(enemy);
            } else {
                safeTargets.add(enemy);
            }
        }

        // Prefer safe targets unless they're all buildings
        boolean safeTargetsOnlyBuildings = safeTargets.stream().allMatch(unit -> unit.getType().isBuilding());
        List<Unit> preferredTargets = (safeTargets.isEmpty() || safeTargetsOnlyBuildings) ? dangerousTargets : safeTargets;

        // Priority: Workers > Anti-air threats > Stray units > Static defense
        List<Unit> workers = findWorkers(preferredTargets);
        if (!workers.isEmpty()) {
            return getClosestUnit(squadCenter, workers);
        }

        List<Unit> antiAirThreats = findAntiAirThreats(preferredTargets);
        if (!antiAirThreats.isEmpty()) {
            return getClosestUnit(squadCenter, antiAirThreats);
        }

        List<Unit> strayUnits = findStrayUnits(preferredTargets);
        if (!strayUnits.isEmpty()) {
            return getClosestUnit(squadCenter, strayUnits);
        }

        List<Unit> staticDefense = findStaticDefense(preferredTargets);
        if (!staticDefense.isEmpty()) {
            return getClosestUnit(squadCenter, staticDefense);
        }

        return preferredTargets.isEmpty() ? null : getClosestUnit(squadCenter, preferredTargets);
    }

    private boolean canAttackTargetFromSafePosition(Unit target, Set<Position> staticDefenseCoverage) {
        return isPositionInStaticDefenseCoverage(target.getPosition(), staticDefenseCoverage) &&
                findSafeAttackPosition(target, staticDefenseCoverage) != null;
    }

    private Position findSafeAttackPosition(Unit target, Set<Position> staticDefenseCoverage) {
        Position targetPos = target.getPosition();
        int mutaRange = UnitType.Zerg_Mutalisk.airWeapon().maxRange();

        List<Position> candidatePositions = new ArrayList<>();

        for (int angle = 0; angle < 360; angle += 15) {
            double radians = Math.toRadians(angle);
            int x = targetPos.getX() + (int)(mutaRange * Math.cos(radians));
            int y = targetPos.getY() + (int)(mutaRange * Math.sin(radians));

            Position candidatePos = new Position(x, y);

            if (!isPositionInStaticDefenseCoverage(candidatePos, staticDefenseCoverage)) {
                candidatePositions.add(candidatePos);
            }
        }

        if (candidatePositions.isEmpty()) {
            return null;
        }

        // Sort by position score (higher is better)
        candidatePositions.sort((pos1, pos2) -> {
            double score1 = calculatePositionScore(pos1, staticDefenseCoverage, null); // GameState not needed for basic scoring
            double score2 = calculatePositionScore(pos2, staticDefenseCoverage, null);
            return Double.compare(score2, score1); // Reverse order for highest first
        });

        return candidatePositions.get(0);
    }

    private Position findSafeRetreatPosition(Position currentPos, Set<Position> staticDefenseCoverage, GameState gameState) {
        int maxRadius = 320;
        int step = 32;

        for (int radius = step; radius <= maxRadius; radius += step) {
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle);
                int x = currentPos.getX() + (int)(radius * Math.cos(radians));
                int y = currentPos.getY() + (int)(radius * Math.sin(radians));

                Position candidatePos = new Position(x, y);

                if (!isPositionInStaticDefenseCoverage(candidatePos, staticDefenseCoverage)) {
                    return candidatePos;
                }
            }
        }

        return null;
    }

    private boolean isPositionInStaticDefenseCoverage(Position pos, Set<Position> staticDefenseCoverage) {
        for (Position coveredPos : staticDefenseCoverage) {
            if (pos.getDistance(coveredPos) < 16) {
                return true;
            }
        }
        return false;
    }

    private boolean isAntiAir(Unit unit) {
        UnitType type = unit.getType();

        // Check if unit can attack air
        if (type.airWeapon() != null && type.airWeapon().maxRange() > 0) {
            return true;
        }

        // Special cases
        if (type == UnitType.Terran_Bunker && !unit.getLoadedUnits().isEmpty()) {
            return true; // Assume bunker has marines
        }

        return false;
    }

    private List<Unit> findWorkers(List<Unit> enemies) {
        List<Unit> workers = new ArrayList<>();
        for (Unit unit : enemies) {
            UnitType type = unit.getType();
            if (type == UnitType.Protoss_Probe || type == UnitType.Terran_SCV || type == UnitType.Zerg_Drone) {
                workers.add(unit);
            }
        }
        return workers;
    }

    private List<Unit> findAntiAirThreats(List<Unit> enemies) {
        List<Unit> threats = new ArrayList<>();
        Position squadCenter = getCenter();

        for (Unit enemy : enemies) {
            if (isAntiAir(enemy) && squadCenter.getDistance(enemy.getPosition()) < 256) {
                threats.add(enemy);
            }
        }
        return threats;
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

            // Units more than 64 pixels from nearest ally are stray
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
                    type == UnitType.Protoss_Photon_Cannon ||
                    type == UnitType.Zerg_Spore_Colony) {
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

    /**
     * Calculates a score for a position based on distance from static defenses and terrain type.
     * Higher score is better.
     */
    private double calculatePositionScore(Position pos, Set<Position> staticDefenseCoverage, GameState gameState) {
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

        if (!gameState.getGame().isBuildable(pos.toTilePosition())) {
            score += 50;
        }

        return score;
    }
}