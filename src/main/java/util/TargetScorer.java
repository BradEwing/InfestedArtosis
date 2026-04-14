package util;

import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;

import java.util.List;

public final class TargetScorer {

    private static final double CURRENT_TARGET_BONUS = 1.2;

    private enum Priority {
        LOW,
        NORMAL,
        ELEVATED,
        HIGH,
        CRITICAL
    }

    public static Unit selectTarget(Unit attacker, List<Unit> candidates, Unit currentTarget) {
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
            Priority priority = assignPriority(candidateType, attackerIsFlying, candidate);
            double score = scoreWithinTier(attacker, candidate, currentTarget);

            if (bestPriority == null
                    || priority.ordinal() > bestPriority.ordinal()
                    || priority == bestPriority && score > bestScore) {
                bestTarget = candidate;
                bestPriority = priority;
                bestScore = score;
            }
        }

        return bestTarget;
    }

    private static Priority assignPriority(UnitType candidateType, boolean attackerIsFlying, Unit candidate) {
        if (candidateType.isBuilding()) {
            if (Filter.isHostileBuilding(candidateType)) {
                boolean canHitMe = canAttackType(candidateType, attackerIsFlying);
                return canHitMe ? Priority.CRITICAL : Priority.NORMAL;
            }
            return Priority.LOW;
        }

        if (Filter.isWorkerType(candidateType)) {
            if (Filter.isMeanWorker(candidate)) {
                return Priority.CRITICAL;
            }
            return Priority.ELEVATED;
        }

        boolean canHitMe = canAttackType(candidateType, attackerIsFlying);
        return canHitMe ? Priority.CRITICAL : Priority.NORMAL;
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
        if (unitType == UnitType.Terran_Bunker) return true;
        if (unitType == UnitType.Protoss_Carrier) return true;
        WeaponType weapon = targetIsFlying ? unitType.airWeapon() : unitType.groundWeapon();
        return weapon != null && weapon != WeaponType.None;
    }
}
