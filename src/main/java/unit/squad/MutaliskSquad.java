package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import info.tracking.PsiStormTracker;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import unit.squad.CombatSimulator.CombatResult;
import unit.squad.cluster.ClusterCombatEvaluator;
import util.Time;
import util.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Specialized squad implementation for Mutalisk units.
 * Handles air unit specific tactics including harassment, retreat calculations,
 * and static defense avoidance.
 */
public class MutaliskSquad extends Squad {

    private boolean shouldDisband = false;

    private Time attackUntilFrame = null;
    private Time retreatUntilFrame = null;
    private static final Time ATTACK_WINDOW = new Time(12);
    private static final Time RETREAT_WINDOW = new Time(36);

    public MutaliskSquad() {
        super();
        ClusterCombatEvaluator evaluator = new ClusterCombatEvaluator();
        this.setCombatSimulator(evaluator);
        this.setClusterEvaluator(evaluator);
        this.setType(UnitType.Zerg_Mutalisk);
    }

    @Override
    public void onFrame() {
        checkRallyTransition();
        super.onFrame();
    }

    @Override
    public boolean shouldDisband() {
        return shouldDisband;
    }

    public void executeTactics(GameState gameState, Position rallyPosition, Set<ManagedUnit> reinforcements) {
        if (members.isEmpty()) {
            shouldDisband = true;
            return;
        }
        int minSize = (gameState.getOpponentRace() == Race.Zerg) ? 2 : 5;
        if (members.size() < minSize) {
            if (rallyPosition != null && status == SquadStatus.RALLY) {
                rallyToPosition(rallyPosition, null);
                return;
            }
            setStatus(SquadStatus.RALLY);
            rallyToSafePosition(gameState);
            return;
        }

        Position rallyPoint = rallyPosition != null ? rallyPosition : gameState.getSquadRallyPoint();

        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();
        List<Unit> allEnemies = new ArrayList<>(enemyUnits);
        allEnemies.removeIf(enemy -> util.Filter.isLowPriorityCombatTarget(enemy.getType()));

        if (allEnemies.isEmpty()) {
            Set<Position> enemyWorkerLocations = gameState.getLastKnownLocationOfEnemyWorkers();
            if (!enemyWorkerLocations.isEmpty()) {
                setStatus(SquadStatus.RALLY);
                rallyToHarassmentPosition(gameState);
                return;
            }

            Set<Position> enemyBuildingPositions = gameState.getLastKnownPositionsOfBuildings();
            if (!enemyBuildingPositions.isEmpty()) {
                setStatus(SquadStatus.RALLY);
                rallyToPosition(findClosestPosition(enemyBuildingPositions), null);
                return;
            }

            shouldDisband = true;
            return;
        }

        Set<Position> staticDefenseCoverage = gameState.getAerielStaticDefenseCoverage();
        Set<Position> stormPositions = gameState.getActiveStormPositions();
        Time currentTime = gameState.getGameTime();

        boolean squadInStorm = !stormPositions.isEmpty() &&
            stormPositions.stream().anyMatch(storm -> getCenter().getDistance(storm) <= 96);

        boolean isAttackWindowActive = attackUntilFrame != null && currentTime.lessThanOrEqual(attackUntilFrame);
        boolean isRetreatWindowActive = retreatUntilFrame != null && currentTime.lessThanOrEqual(retreatUntilFrame);

        if (squadInStorm) {
            setStatus(SquadStatus.RETREAT);
            executeRetreat(gameState, stormPositions, rallyPoint);
            return;
        }

        if (!isAttackWindowActive && !isRetreatWindowActive) {
            CombatResult combatResult = getCombatSimulator().evaluate(this, reinforcements, gameState);
            if (combatResult == CombatResult.RETREAT) {
                retreatUntilFrame = currentTime.add(RETREAT_WINDOW);
            } else {
                attackUntilFrame = currentTime.add(ATTACK_WINDOW);
            }
        }

        if (isRetreatWindowActive) {
            setStatus(SquadStatus.RETREAT);
            executeRetreat(gameState, stormPositions, rallyPoint);
            return;
        }

        if (isAttackWindowActive) {
            setStatus(SquadStatus.FIGHT);
        } else {
            attackUntilFrame = null;
            retreatUntilFrame = null;
        }

        Unit priorityTarget = findPriorityTarget(allEnemies, staticDefenseCoverage);

        for (ManagedUnit mutalisk : getMembers()) {
            mutalisk.setRole(UnitRole.FIGHT);
            mutalisk.setReady(true);
            if (priorityTarget != null) {
                mutalisk.setFightTarget(priorityTarget);
            }
            Position stormRetreat = calculateStormRetreat(mutalisk.getUnit(), stormPositions, gameState);
            mutalisk.setRetreatTarget(stormRetreat != null ? stormRetreat : rallyPoint);
            mutalisk.setRallyPoint(rallyPoint);
        }

        if (priorityTarget != null) {
            setTarget(priorityTarget);
        }
    }

    private void executeRetreat(GameState gameState, Set<Position> stormPositions, Position rallyPoint) {
        for (ManagedUnit mutalisk : getMembers()) {
            mutalisk.setRole(UnitRole.RETREAT);
            mutalisk.setReady(true);
            Position stormRetreat = calculateStormRetreat(mutalisk.getUnit(), stormPositions, gameState);
            mutalisk.setRetreatTarget(stormRetreat != null ? stormRetreat : rallyPoint);
            mutalisk.setRallyPoint(rallyPoint);
        }
    }

    private void rallyToHarassmentPosition(GameState gameState) {
        Set<Position> enemyWorkerLocations = gameState.getLastKnownLocationOfEnemyWorkers();
        Position rallyPoint = enemyWorkerLocations.isEmpty()
            ? gameState.getSquadRallyPoint()
            : findClosestPosition(enemyWorkerLocations);
        rallyToPosition(rallyPoint, null);
    }

    private void rallyToSafePosition(GameState gameState) {
        rallyToPosition(gameState.getSquadRallyPoint(), null);
    }

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

    private Position findClosestPosition(Set<Position> positions) {
        Position squadCenter = getCenter();
        Position closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Position position : positions) {
            double distance = squadCenter.getDistance(position);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = position;
            }
        }
        return closest != null ? closest : squadCenter;
    }

    private Position calculateStormRetreat(Unit mutalisk, Set<Position> stormPositions, GameState gameState) {
        if (stormPositions.isEmpty()) return null;

        Position mutaliskPos = mutalisk.getPosition();
        Position nearestStorm = null;
        double nearestDist = Double.MAX_VALUE;
        for (Position stormPos : stormPositions) {
            double dist = mutaliskPos.getDistance(stormPos);
            if (dist < nearestDist) {
                nearestStorm = stormPos;
                nearestDist = dist;
            }
        }

        if (nearestStorm != null && nearestDist <= PsiStormTracker.STORM_RADIUS) {
            Vec2 away = Vec2.between(nearestStorm, mutaliskPos);
            if (away.length() == 0) {
                away = Vec2.between(mutaliskPos, getCenter());
                if (away.length() == 0) away = new Vec2(0, 1);
            }
            return away.normalizeToLength(256).clampToMap(gameState.getGame(), mutaliskPos);
        }

        return null;
    }

    private Unit findPriorityTarget(List<Unit> enemies, Set<Position> staticDefenseCoverage) {
        Position squadCenter = getCenter();
        if (enemies.isEmpty()) return null;

        List<Unit> safe = new ArrayList<>();
        List<Unit> unsafe = new ArrayList<>();
        for (Unit e : enemies) {
            if (isPositionInStaticDefenseCoverage(e.getPosition(), staticDefenseCoverage)) {
                unsafe.add(e);
            } else {
                safe.add(e);
            }
        }
        List<Unit> candidates = safe.isEmpty() ? unsafe : safe;

        List<Unit> aaUnits = new ArrayList<>();
        List<Unit> aaStatic = new ArrayList<>();
        List<Unit> workers = new ArrayList<>();
        List<Unit> others = new ArrayList<>();
        for (Unit u : candidates) {
            if (isAntiAir(u) && !u.getType().isBuilding()) aaUnits.add(u);
            else if (isAAStaticDefense(u)) aaStatic.add(u);
            else if (isWorker(u)) workers.add(u);
            else others.add(u);
        }

        if (!aaUnits.isEmpty()) return getClosestUnit(squadCenter, aaUnits);
        if (!aaStatic.isEmpty()) return getClosestUnit(squadCenter, aaStatic);
        if (!workers.isEmpty()) return getClosestUnit(squadCenter, workers);
        return getClosestUnit(squadCenter, others);
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
        return (type.airWeapon() != null && type.airWeapon().maxRange() > 0)
                || (type == UnitType.Terran_Bunker && !unit.getLoadedUnits().isEmpty());
    }

    private boolean isAAStaticDefense(Unit unit) {
        UnitType type = unit.getType();
        return type == UnitType.Terran_Missile_Turret ||
               type == UnitType.Terran_Bunker ||
               type == UnitType.Protoss_Photon_Cannon ||
               type == UnitType.Zerg_Spore_Colony;
    }

    private boolean isWorker(Unit unit) {
        return unit.getType().isWorker();
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

    private void checkRallyTransition() {
        boolean isRallyOrRetreat = getStatus() == SquadStatus.RALLY || getStatus() == SquadStatus.RETREAT;
        if (!isRallyOrRetreat) return;

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

        if (mutaliskCount > 0 && (double)mutaliskAtRally / mutaliskCount >= 0.75) {
            setStatus(SquadStatus.FIGHT);
            for (ManagedUnit mutalisk : getMembers()) {
                mutalisk.setRole(UnitRole.FIGHT);
                mutalisk.setReady(true);
            }
        }
    }
}
