package util;

import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a fight target from a candidate list. Every candidate is assigned a {@link Priority} tier and
 * the highest tier wins outright; within a tier, nearer, more injured candidates score higher and the
 * current target keeps a stickiness bonus.
 *
 * <p>Tiers, highest first:
 * <ul>
 *   <li>CRITICAL: anything that can attack the attacker's layer, and workers that are attacking</li>
 *   <li>ELEVATED: other workers</li>
 *   <li>NORMAL: mobile units that cannot attack the attacker's layer</li>
 *   <li>LOW: buildings, including hostile buildings that cannot attack the attacker's layer</li>
 * </ul>
 */
public final class TargetScorer {

    private static final double CURRENT_TARGET_BONUS = 1.2;

    public enum Priority {
        LOW,
        NORMAL,
        ELEVATED,
        CRITICAL
    }

    private TargetScorer() {
    }

    /**
     * @return the chosen target with the tier it was assigned, or null when there are no candidates
     */
    public static Selection selectTarget(Unit attacker, List<Unit> candidates, Unit currentTarget) {
        if (candidates.isEmpty()) {
            return null;
        }

        boolean attackerIsFlying = attacker.isFlying();

        if (candidates.size() == 1) {
            Unit only = candidates.get(0);
            return new Selection(only, assignPriority(only.getType(), attackerIsFlying, Filter.isMeanWorker(only)), 1);
        }

        List<Candidate> scored = new ArrayList<>(candidates.size());
        for (Unit candidate : candidates) {
            scored.add(toCandidate(attacker, candidate, currentTarget));
        }

        int best = selectIndex(attackerIsFlying, scored);
        return new Selection(candidates.get(best), scored.get(best).priority(attackerIsFlying), candidates.size());
    }

    /**
     * @return index of the best candidate: highest tier first, then highest within-tier score. Ties keep
     *     the earlier candidate.
     */
    static int selectIndex(boolean attackerIsFlying, List<Candidate> candidates) {
        int bestIndex = -1;
        Priority bestPriority = null;
        double bestScore = -1;

        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            Priority priority = candidate.priority(attackerIsFlying);
            double score = candidate.score();

            if (bestPriority == null
                    || priority.ordinal() > bestPriority.ordinal()
                    || priority == bestPriority && score > bestScore) {
                bestIndex = i;
                bestPriority = priority;
                bestScore = score;
            }
        }

        return bestIndex;
    }

    static Priority assignPriority(UnitType candidateType, boolean attackerIsFlying, boolean isMeanWorker) {
        if (candidateType.isBuilding()) {
            if (Filter.isHostileBuilding(candidateType) && canAttackType(candidateType, attackerIsFlying)) {
                return Priority.CRITICAL;
            }
            return Priority.LOW;
        }

        if (Filter.isWorkerType(candidateType)) {
            return isMeanWorker ? Priority.CRITICAL : Priority.ELEVATED;
        }

        boolean canHitMe = canAttackType(candidateType, attackerIsFlying);
        return canHitMe ? Priority.CRITICAL : Priority.NORMAL;
    }

    private static Candidate toCandidate(Unit attacker, Unit candidate, Unit currentTarget) {
        UnitType type = candidate.getType();
        double hpFraction = (double) (candidate.getHitPoints() + candidate.getShields())
                / (type.maxHitPoints() + type.maxShields());
        boolean isCurrent = currentTarget != null && candidate.getID() == currentTarget.getID();
        return new Candidate(type, attacker.getDistance(candidate), hpFraction, isCurrent,
                Filter.isMeanWorker(candidate));
    }

    private static boolean canAttackType(UnitType unitType, boolean targetIsFlying) {
        if (unitType == UnitType.Terran_Bunker) return true;
        if (unitType == UnitType.Protoss_Carrier) return true;
        WeaponType weapon = targetIsFlying ? unitType.airWeapon() : unitType.groundWeapon();
        return weapon != null && weapon != WeaponType.None;
    }

    /**
     * The scorer's inputs for one candidate, read from BWAPI once so ranking needs no live unit.
     */
    static final class Candidate {
        private final UnitType type;
        private final int distance;
        private final double hpFraction;
        private final boolean currentTarget;
        private final boolean meanWorker;

        Candidate(UnitType type, int distance, double hpFraction, boolean currentTarget, boolean meanWorker) {
            this.type = type;
            this.distance = distance;
            this.hpFraction = hpFraction;
            this.currentTarget = currentTarget;
            this.meanWorker = meanWorker;
        }

        UnitType type() {
            return type;
        }

        Priority priority(boolean attackerIsFlying) {
            return assignPriority(type, attackerIsFlying, meanWorker);
        }

        double score() {
            int clamped = Math.max(distance, 1);
            double injuryBonus = 1.0 + 0.5 * (1.0 - hpFraction);
            double currentTargetBonus = currentTarget ? CURRENT_TARGET_BONUS : 1.0;
            return injuryBonus * currentTargetBonus / clamped;
        }
    }

    /**
     * A chosen target, the tier it was assigned, and how many candidates it was chosen from.
     */
    public static final class Selection {
        private final Unit target;
        private final Priority priority;
        private final int candidateCount;

        public Selection(Unit target, Priority priority, int candidateCount) {
            this.target = target;
            this.priority = priority;
            this.candidateCount = candidateCount;
        }

        public Unit getTarget() {
            return target;
        }

        public Priority getPriority() {
            return priority;
        }

        public int getCandidateCount() {
            return candidateCount;
        }
    }
}
