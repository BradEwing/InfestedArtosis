package util;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;

import java.util.List;
import java.util.Set;

public final class TargetScorer {

    private static final double CURRENT_TARGET_BONUS = 1.2;
    private static final int WORKER_NEAR_BASE_RADIUS = 512;

    private enum Priority {
        LOW,
        NORMAL,
        ELEVATED,
        HIGH,
        CRITICAL
    }

    public static Unit selectTarget(Unit attacker, List<Unit> candidates, Unit currentTarget, Set<Position> friendlyBasePositions) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        boolean attackerIsFlying = attacker.isFlying();

        Unit bestTarget = null;
        Priority bestPriority = null;
        double bestScore = -1;

        for (Unit candidate : candidates) {
            UnitType candidateType = candidate.getType();
            Priority priority = assignPriority(candidateType, attackerIsFlying, candidate, friendlyBasePositions);
            double score = scoreWithinTier(attacker, candidate, currentTarget);

            if (bestPriority == null
                    || priority.ordinal() > bestPriority.ordinal()
                    || (priority == bestPriority && score > bestScore)) {
                bestTarget = candidate;
                bestPriority = priority;
                bestScore = score;
            }
        }

        return bestTarget;
    }

    private static Priority assignPriority(UnitType candidateType, boolean attackerIsFlying, Unit candidate, Set<Position> friendlyBasePositions) {
        if (candidateType.isBuilding()) {
            if (Filter.isHostileBuilding(candidateType)) {
                boolean canHitMe = canAttackType(candidateType, attackerIsFlying);
                return canHitMe ? Priority.CRITICAL : Priority.ELEVATED;
            }
            return Priority.LOW;
        }

        if (Filter.isWorkerType(candidateType)) {
            if (Filter.isMeanWorker(candidate)) {
                return Priority.ELEVATED;
            }
            if (isNearFriendlyBase(candidate, friendlyBasePositions)) {
                return Priority.ELEVATED;
            }
            return Priority.NORMAL;
        }

        boolean canHitMe = canAttackType(candidateType, attackerIsFlying);
        return canHitMe ? Priority.HIGH : Priority.NORMAL;
    }

    private static double scoreWithinTier(Unit attacker, Unit candidate, Unit currentTarget) {
        int distance = attacker.getDistance(candidate);
        if (distance <= 0) {
            distance = 1;
        }

        double hpFraction = (double) (candidate.getHitPoints() + candidate.getShields())
                / (candidate.getType().maxHitPoints() + candidate.getType().maxShields());
        double injuryBonus = 1.0 + 0.5 * (1.0 - hpFraction);

        double currentTargetBonus = (currentTarget != null && candidate.getID() == currentTarget.getID())
                ? CURRENT_TARGET_BONUS : 1.0;

        return injuryBonus * currentTargetBonus / distance;
    }

    private static boolean canAttackType(UnitType unitType, boolean targetIsFlying) {
        WeaponType weapon = targetIsFlying ? unitType.airWeapon() : unitType.groundWeapon();
        return weapon != null && weapon != WeaponType.None;
    }

    private static boolean isNearFriendlyBase(Unit unit, Set<Position> basePositions) {
        if (basePositions == null || basePositions.isEmpty()) {
            return false;
        }
        Position unitPos = unit.getPosition();
        for (Position basePos : basePositions) {
            if (unitPos.getDistance(basePos) <= WORKER_NEAR_BASE_RADIUS) {
                return true;
            }
        }
        return false;
    }
}
