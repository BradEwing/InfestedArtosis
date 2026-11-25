package unit.squad;

import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import util.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combat simulator implementation for Scourge squads.
 * Evaluates whether scourge should kamikaze attack based on kill potential.
 * Scourge will engage if they can kill at least 1 enemy air unit. Interceptors are not counted as air units to
 * kill, see {@link InterceptorTargeting}, so a squad that sees only Interceptors retreats.
 */
public class ScourgeCombatSimulator implements CombatSimulator {

    /**
     * An enemy air unit as the simulator reads it: its type and its remaining hit points plus shields.
     */
    static final class AirTarget {
        private final UnitType type;
        private final int hitPoints;

        AirTarget(UnitType type, int hitPoints) {
            this.type = type;
            this.hitPoints = hitPoints;
        }
    }

    private boolean airTargetSeen;

    /**
     * @return true when the last evaluation saw an enemy air unit that counts as an air target
     */
    boolean isAirTargetSeen() {
        return airTargetSeen;
    }

    @Override
    public CombatResult evaluate(Squad squad, Map<Squad, Double> adjacentSquads, GameState gameState) {
        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();

        List<AirTarget> seen = new ArrayList<>();
        for (Unit enemy : enemyUnits) {
            if (enemy.isFlying() && enemy.isDetected() && !enemy.isInvincible()) {
                seen.add(new AirTarget(enemy.getType(), enemy.getHitPoints() + enemy.getShields()));
            }
        }
        for (Unit building : enemyBuildings) {
            if (building.isFlying() && building.isDetected() && !building.isInvincible()) {
                seen.add(new AirTarget(building.getType(), building.getHitPoints() + building.getShields()));
            }
        }

        airTargetSeen = !airTargets(seen).isEmpty();
        int squadSize = squad.size();
        if (squadSize == 0 || !airTargetSeen) {
            return CombatResult.RETREAT;
        }

        int scourgeDamage = squad.getMembers().iterator().next().getUnit().getPlayer()
                .damage(UnitType.Zerg_Scourge.airWeapon());
        return verdict(squadSize, scourgeDamage, seen);
    }

    /**
     * Whether an enemy air unit of the given type counts as something for Scourge to kill. Larvae, eggs and cocoons
     * do not, and neither do Interceptors.
     *
     * @param type the enemy air unit's type
     * @return true when Scourge count the type as an air target
     */
    static boolean isAirTarget(UnitType type) {
        return !Filter.isLowPriorityCombatTarget(type)
                && InterceptorTargeting.admits(UnitType.Zerg_Scourge, type);
    }

    /**
     * Decides whether a Scourge squad engages the enemy air units it sees: it does when it can kill at least one
     * of those that count as air targets (see {@link #isAirTarget}), and retreats otherwise.
     *
     * @param squadSize the number of Scourge in the squad
     * @param scourgeDamage the damage one Scourge deals
     * @param seen the detected, vulnerable enemy air units and floating buildings
     * @return ENGAGE when the squad can kill an air target, RETREAT otherwise
     */
    static CombatResult verdict(int squadSize, int scourgeDamage, List<AirTarget> seen) {
        List<AirTarget> airTargets = airTargets(seen);
        if (squadSize <= 0 || airTargets.isEmpty()) {
            return CombatResult.RETREAT;
        }
        return canKillTarget(squadSize, scourgeDamage, airTargets) ? CombatResult.ENGAGE : CombatResult.RETREAT;
    }

    private static List<AirTarget> airTargets(List<AirTarget> seen) {
        List<AirTarget> airTargets = new ArrayList<>();
        for (AirTarget target : seen) {
            if (isAirTarget(target.type)) {
                airTargets.add(target);
            }
        }
        return airTargets;
    }

    /**
     * Evaluates if the scourge squad can kill at least one enemy air unit.
     */
    private static boolean canKillTarget(int squadSize, int scourgeDamage, List<AirTarget> airTargets) {
        List<AirTarget> prioritizedTargets = prioritizeTargets(airTargets);

        int remainingScourge = squadSize;

        for (AirTarget target : prioritizedTargets) {
            if (remainingScourge <= 0) {
                break;
            }

            int scourgeNeeded = (int) Math.ceil((double) target.hitPoints / scourgeDamage);

            if (scourgeNeeded <= remainingScourge) {
                return true;
            }

            if (isHighPriorityTarget(target.type)) {
                remainingScourge = Math.max(0, remainingScourge - scourgeNeeded);
            }
        }

        return false;
    }

    private static List<AirTarget> prioritizeTargets(List<AirTarget> airTargets) {
        List<AirTarget> highPriority = new ArrayList<>();
        List<AirTarget> mediumPriority = new ArrayList<>();
        List<AirTarget> lowPriority = new ArrayList<>();

        for (AirTarget target : airTargets) {
            UnitType type = target.type;

            if (isLowPriorityTarget(type)) {
                lowPriority.add(target);
            } else if (isFloatingBuilding(type)) {
                mediumPriority.add(target);
            } else {
                highPriority.add(target);
            }
        }

        List<AirTarget> prioritized = new ArrayList<>();
        prioritized.addAll(highPriority);
        prioritized.addAll(mediumPriority);
        prioritized.addAll(lowPriority);

        return prioritized;
    }

    private static boolean isHighPriorityTarget(UnitType type) {
        return !isFloatingBuilding(type) && !isLowPriorityTarget(type);
    }

    private static boolean isFloatingBuilding(UnitType type) {
        return type.isFlyingBuilding();
    }

    private static boolean isLowPriorityTarget(UnitType type) {
        return type == UnitType.Zerg_Overlord;
    }
}
