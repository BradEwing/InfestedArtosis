package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import unit.squad.CombatSimulator.CombatResult;
import util.Time;

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
    private boolean shouldDisband = false;
    
    // Attack and retreat timers for hysteresis behavior
    private Time attackUntilFrame = null;
    private Time retreatUntilFrame = null;
    private static final Time ATTACK_WINDOW = new Time(12); // 0.5 second
    private static final Time RETREAT_WINDOW = new Time(36); // 1.5 seconds

    public MutaliskSquad() {
        super();
        this.combatSimulator = new MutaliskCombatSimulator();
        this.setType(UnitType.Zerg_Mutalisk);
    }

    @Override
    public void onFrame() {
        checkRallyTransition();
        super.onFrame();
    }

    /**
     * Returns true if this squad should be disbanded due to lack of targets.
     */
    @Override
    public boolean shouldDisband() {
        return shouldDisband;
    }

    /**
     * Executes mutalisk-specific squad behavior including target selection,
     * retreat calculations, and engagement decisions.
     */
    public void executeTactics(GameState gameState) {
        int minSize = (gameState.getOpponentRace() == Race.Zerg) ? 2 : 5;
        if (getMembers().size() < minSize) {
            setStatus(SquadStatus.RALLY);
            rallyToSafePosition(gameState);
            return;
        }

        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();

        List<Unit> allEnemies = new ArrayList<>();
        allEnemies.addAll(enemyUnits);
        
        // Filter out low priority targets
        allEnemies.removeIf(enemy -> util.Filter.isLowPriorityCombatTarget(enemy.getType()));

        // Check if we should disband due to no targets
        if (allEnemies.isEmpty()) {
            Set<Position> enemyWorkerLocations = gameState.getLastKnownLocationOfEnemyWorkers();

            if (enemyWorkerLocations.isEmpty()) {
                shouldDisband = true;
                return;
            }

            // We have worker locations, rally to harassment position
            setStatus(SquadStatus.RALLY);
            rallyToHarassmentPosition(gameState);
            return;
        }

        Set<Position> staticDefenseCoverage = gameState.getAerielStaticDefenseCoverage();

        Time currentTime = gameState.getGameTime();
        
        // Check if timers are active
        boolean isAttackWindowActive = attackUntilFrame != null && currentTime.lessThanOrEqual(attackUntilFrame);
        boolean isRetreatWindowActive = retreatUntilFrame != null && currentTime.lessThanOrEqual(retreatUntilFrame);
        
        // Only re-evaluate combat when both timers are expired or null
        if (!isAttackWindowActive && !isRetreatWindowActive) {
            CombatResult combatResult = combatSimulator.evaluate(this, gameState);
            
            // Set appropriate timer based on combat result
            if (combatResult == CombatResult.RETREAT) {
                retreatUntilFrame = currentTime.add(RETREAT_WINDOW);
            } else {
                attackUntilFrame = currentTime.add(ATTACK_WINDOW);
            }
        }
        
        // Determine behavior based on active timers
        if (isRetreatWindowActive) {
            setStatus(SquadStatus.RETREAT);
            executeRetreat(gameState, allEnemies, staticDefenseCoverage);
            return;
        }
        
        if (isAttackWindowActive) {
            setStatus(SquadStatus.FIGHT);
            // Attack behavior continues below
        } else {
            // If no timer is active, clear both for next evaluation
            attackUntilFrame = null;
            retreatUntilFrame = null;
        }

        // Find priority target
        Unit priorityTarget = findPriorityTarget(allEnemies, staticDefenseCoverage);

        if (priorityTarget != null) {
            Position safeAttackPos = findSafeAttackPosition(priorityTarget, staticDefenseCoverage);
            if (safeAttackPos != null) {
                double distanceToSafe = getCenter().getDistance(safeAttackPos);
                if (!isAttackWindowActive && distanceToSafe > 96) {
                    setStatus(SquadStatus.RALLY);
                    setTarget(priorityTarget);
                    rallyToPosition(safeAttackPos, priorityTarget);
                    return;
                }
                // Close enough to safe ring: proceed to fight below
            }
        }

        // Default fight behavior
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

        // Partition by distance: near first (<256), else far
        List<Unit> near = new ArrayList<>();
        List<Unit> far = new ArrayList<>();
        for (Unit e : enemies) {
            (squadCenter.getDistance(e.getPosition()) < 256 ? near : far).add(e);
        }
        List<Unit> working = !near.isEmpty() ? near : far;
        if (working.isEmpty()) {
            return null;
        }

        // Prefer targets not covered by static AA
        List<Unit> safe = new ArrayList<>();
        List<Unit> unsafe = new ArrayList<>();
        for (Unit e : working) {
            if (isPositionInStaticDefenseCoverage(e.getPosition(), staticDefenseCoverage)) unsafe.add(e); else safe.add(e);
        }
        List<Unit> preferred = safe.isEmpty() ? unsafe : safe;

        // Edge-aware: compute hull of local cluster and filter to hull units closest to squad
        List<Position> localPositions = new ArrayList<>();
        for (Unit e : preferred) {
            if (squadCenter.getDistance(e.getPosition()) <= 384) {
                if (!(e.getType().isBuilding() && !isAAStaticDefense(e))) {
                    localPositions.add(e.getPosition());
                }
            }
        }
        List<Position> hull = computeConvexHull(localPositions);
        List<Unit> edgeUnits = unitsOnHull(hull, preferred);
        List<Unit> candidateEdge = edgeTargetsNearSquad(edgeUnits, squadCenter, 8);
        List<Unit> candidates = !candidateEdge.isEmpty() ? candidateEdge : preferred;

        // Priority buckets within candidates: anti-air > AA static defense > workers > others
        List<Unit> aaUnits = new ArrayList<>();
        List<Unit> aaStatic = new ArrayList<>();
        List<Unit> workers = new ArrayList<>();
        List<Unit> others = new ArrayList<>();
        for (Unit u : candidates) {
            if (isAntiAir(u) && !u.getType().isBuilding()) {
                aaUnits.add(u);
            } else if (isAAStaticDefense(u)) {
                aaStatic.add(u);
            } else if (isWorker(u)) {
                workers.add(u);
            } else {
                others.add(u);
            }
        }

        if (!aaUnits.isEmpty()) return getClosestUnit(squadCenter, aaUnits);
        if (!aaStatic.isEmpty()) return getClosestUnit(squadCenter, aaStatic);
        if (!workers.isEmpty()) return getClosestUnit(squadCenter, workers);
        return getClosestUnit(squadCenter, others);
    }

    private List<Position> computeConvexHull(List<Position> points) {
        List<Position> pts = new ArrayList<>(points);
        if (pts.size() < 3) return pts;
        pts.sort((a,b) -> a.getX() == b.getX() ? Integer.compare(a.getY(), b.getY()) : Integer.compare(a.getX(), b.getX()));
        List<Position> lower = new ArrayList<>();
        for (Position p : pts) {
            while (lower.size() >= 2 && cross(lower.get(lower.size()-2), lower.get(lower.size()-1), p) <= 0) {
                lower.remove(lower.size()-1);
            }
            lower.add(p);
        }
        List<Position> upper = new ArrayList<>();
        for (int i = pts.size()-1; i >= 0; i--) {
            Position p = pts.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size()-2), upper.get(upper.size()-1), p) <= 0) {
                upper.remove(upper.size()-1);
            }
            upper.add(p);
        }
        lower.remove(lower.size()-1);
        upper.remove(upper.size()-1);
        lower.addAll(upper);
        return lower;
    }

    private long cross(Position a, Position b, Position c) {
        long x1 = b.getX() - a.getX();
        long y1 = b.getY() - a.getY();
        long x2 = c.getX() - b.getX();
        long y2 = c.getY() - b.getY();
        return x1 * y2 - y1 * x2;
    }

    private List<Unit> unitsOnHull(List<Position> hull, List<Unit> enemies) {
        List<Unit> result = new ArrayList<>();
        for (Unit e : enemies) {
            Position p = e.getPosition();
            for (Position h : hull) {
                if (p.getX() == h.getX() && p.getY() == h.getY()) {
                    result.add(e);
                    break;
                }
            }
        }
        return result;
    }

    private List<Unit> edgeTargetsNearSquad(List<Unit> edgeUnits, Position squadCenter, int k) {
        edgeUnits.sort((u1, u2) -> Double.compare(squadCenter.getDistance(u1.getPosition()), squadCenter.getDistance(u2.getPosition())));
        if (edgeUnits.size() <= k) return edgeUnits;
        return new ArrayList<>(edgeUnits.subList(0, k));
    }

    private boolean isAAStaticDefense(Unit unit) {
        UnitType type = unit.getType();
        return type == UnitType.Terran_Missile_Turret ||
               type == UnitType.Terran_Bunker ||
               type == UnitType.Protoss_Photon_Cannon ||
               type == UnitType.Zerg_Spore_Colony;
    }

    private boolean isWorker(Unit unit) {
        UnitType t = unit.getType();
        return t == UnitType.Protoss_Probe || t == UnitType.Terran_SCV || t == UnitType.Zerg_Drone;
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

        for (Unit enemy : enemies) {
            if (isAntiAir(enemy)) {
                threats.add(enemy);
            }
        }
        return threats;
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
     * Checks if mutalisks should transition from rally to fight status.
     * Called when squad is in RALLY or RETREAT status to determine if ready to engage.
     */
    private void checkRallyTransition() {
        boolean isRallyOrRetreat = getStatus() == SquadStatus.RALLY || getStatus() == SquadStatus.RETREAT;
        if (!isRallyOrRetreat) {
            return;
        }

        int mutaliskCount = getMembers().size();
        int mutaliskAtRally = 0;

        for (ManagedUnit mutalisk : getMembers()) {
            if (mutalisk.getRallyPoint() != null) {
                double distanceToRally = mutalisk.getUnit().getPosition().getDistance(mutalisk.getRallyPoint());
                if (distanceToRally < 64) {
                    mutaliskAtRally++;
                }
            }
        }

        // If 75% of mutalisks have reached rally point, transition to fight
        if (mutaliskCount > 0 && (double)mutaliskAtRally / mutaliskCount >= 0.75) {
            setStatus(SquadStatus.FIGHT);

            for (ManagedUnit mutalisk : getMembers()) {
                mutalisk.setRole(UnitRole.FIGHT);
                mutalisk.setReady(true);
            }
        }
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

        if (gameState != null && !gameState.getGame().isBuildable(pos.toTilePosition())) {
            score += 50;
        }

        return score;
    }
}