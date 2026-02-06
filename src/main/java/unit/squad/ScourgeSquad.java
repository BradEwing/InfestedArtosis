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
 * Specialized squad implementation for Scourge units.
 * Handles kamikaze tactics against air units with target prioritization.
 * Scourge will fearlessly engage if they can kill at least one enemy air unit.
 */
public class ScourgeSquad extends Squad {

    private static final int PRIORITY_SCORE_MULTIPLIER = 1000;
    private static final int RALLY_ARRIVAL_DISTANCE = 64;
    private static final double RALLY_TRANSITION_THRESHOLD = 0.75;

    private final CombatSimulator combatSimulator;

    public ScourgeSquad() {
        super();
        this.combatSimulator = new ScourgeCombatSimulator();
        this.setType(UnitType.Zerg_Scourge);
    }

    @Override
    public void onFrame() {
        checkRallyTransition();
        super.onFrame();
    }

    /**
     * Executes scourge-specific squad behavior including kamikaze attacks.
     * Scourge will engage if they can kill at least one air target.
     */
    public void executeTactics(GameState gameState) {
        if (getMembers().size() < 2) {
            setStatus(SquadStatus.RALLY);
            rallyToSafePosition(gameState);
            return;
        }

        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();

        // Find all air targets
        List<Unit> airTargets = new ArrayList<>();
        for (Unit enemy : enemyUnits) {
            if (enemy.isFlying() && enemy.isDetected() && !enemy.isInvincible() && 
                !util.Filter.isLowPriorityCombatTarget(enemy.getType())) {
                airTargets.add(enemy);
            }
        }
        for (Unit building : enemyBuildings) {
            if (building.isFlying() && building.isDetected() && !building.isInvincible()) {
                airTargets.add(building);
            }
        }

        if (airTargets.isEmpty()) {
            setStatus(SquadStatus.RALLY);
            rallyToHuntPosition(gameState);
            return;
        }

        // Evaluate engagement using combat simulator
        CombatSimulator.CombatResult combatResult = combatSimulator.evaluate(this, gameState);

        if (combatResult == CombatSimulator.CombatResult.ENGAGE) {
            setStatus(SquadStatus.FIGHT);
            executeKamikazeAttack(airTargets);
        } else {
            setStatus(SquadStatus.RALLY);
            rallyToSafePosition(gameState);
        }
    }

    /**
     * Executes kamikaze attack on prioritized air targets.
     */
    private void executeKamikazeAttack(List<Unit> airTargets) {
        List<Unit> prioritizedTargets = prioritizeTargets(airTargets);

        for (ManagedUnit scourge : getMembers()) {
            scourge.setRole(UnitRole.FIGHT);
            scourge.setReady(true);

            // Find best target for this scourge
            Unit target = findBestTargetForScourge(scourge, prioritizedTargets);
            if (target != null) {
                scourge.setFightTarget(target);
            }
        }
    }

    /**
     * Finds the best air target for an individual scourge.
     */
    private Unit findBestTargetForScourge(ManagedUnit scourge, List<Unit> prioritizedTargets) {
        Position scourgePos = scourge.getUnit().getPosition();
        Unit bestTarget = null;
        double bestScore = Double.MIN_VALUE;

        for (Unit target : prioritizedTargets) {
            if (!target.isFlying()) continue;

            double distance = scourgePos.getDistance(target.getPosition());
            double priority = getTargetPriority(target.getType());
            double score = priority * PRIORITY_SCORE_MULTIPLIER - distance;

            if (score > bestScore) {
                bestScore = score;
                bestTarget = target;
            }
        }

        return bestTarget;
    }

    /**
     * Prioritizes air targets for scourge attacks.
     * Returns higher values for higher priority targets.
     */
    private List<Unit> prioritizeTargets(List<Unit> airTargets) {
        List<Unit> highPriority = new ArrayList<>();
        List<Unit> mediumPriority = new ArrayList<>();
        List<Unit> lowPriority = new ArrayList<>();

        for (Unit target : airTargets) {
            UnitType type = target.getType();

            if (isLowPriorityTarget(type)) {
                lowPriority.add(target);
            } else if (isFloatingBuilding(type)) {
                mediumPriority.add(target);
            } else {
                highPriority.add(target);
            }
        }

        // Combine lists in priority order
        List<Unit> prioritized = new ArrayList<>();
        prioritized.addAll(highPriority);
        prioritized.addAll(mediumPriority);
        prioritized.addAll(lowPriority);

        return prioritized;
    }

    /**
     * Gets numeric priority for target type (higher = more important).
     */
    private double getTargetPriority(UnitType type) {
        if (isLowPriorityTarget(type)) {
            return 2.0;
        } else if (isFloatingBuilding(type)) {
            return 1.0;
        } else {
            return 3.0;
        }
    }

    /**
     * Rally scourge to safe position when not engaging.
     */
    private void rallyToSafePosition(GameState gameState) {
        Position homeBase = gameState.getSquadRallyPoint();
        rallyToPosition(homeBase);
    }

    /**
     * Rally scourge to hunt for air targets.
     */
    private void rallyToHuntPosition(GameState gameState) {
        // Look for likely air unit locations (enemy bases, expansions)
        Position huntPosition = gameState.getSquadRallyPoint();

        // If we know enemy bases, patrol near them
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();
        if (!enemyBuildings.isEmpty()) {
            Unit enemyBuilding = enemyBuildings.iterator().next();
            huntPosition = enemyBuilding.getPosition();
        }

        rallyToPosition(huntPosition);
    }

    /**
     * Rally all scourge to specified position.
     */
    private void rallyToPosition(Position position) {
        for (ManagedUnit scourge : getMembers()) {
            scourge.setRole(UnitRole.RALLY);
            scourge.setReady(true);
            scourge.setRallyPoint(position);
        }
    }

    /**
     * Checks if scourge should transition from rally to fight status.
     */
    private void checkRallyTransition() {
        if (getStatus() != SquadStatus.RALLY) {
            return;
        }

        int scourgeCount = getMembers().size();
        int scourgeAtRally = 0;

        for (ManagedUnit scourge : getMembers()) {
            if (scourge.getRallyPoint() != null) {
                double distanceToRally = scourge.getUnit().getPosition().getDistance(scourge.getRallyPoint());
                if (distanceToRally < RALLY_ARRIVAL_DISTANCE) {
                    scourgeAtRally++;
                }
            }
        }

        if (scourgeCount > 0 && (double) scourgeAtRally / scourgeCount >= RALLY_TRANSITION_THRESHOLD) {
            setStatus(SquadStatus.FIGHT);

            for (ManagedUnit scourge : getMembers()) {
                scourge.setRole(UnitRole.FIGHT);
                scourge.setReady(true);
            }
        }
    }

    private boolean isFloatingBuilding(UnitType type) {
        return type.isFlyingBuilding();
    }

    private boolean isLowPriorityTarget(UnitType type) {
        return type == UnitType.Zerg_Overlord ||
                type == UnitType.Protoss_Interceptor;
    }
}