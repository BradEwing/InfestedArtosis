package util;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import unit.squad.horizon.HorizonCombatSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Picks a fight target from a candidate list. Every candidate is assigned a {@link Priority} tier and
 * the highest tier wins outright. Within a tier, a melee attacker prefers a target that is not yet
 * saturated with melee attackers; after that, nearer, more injured candidates score higher,
 * a ground attacker's score halves for a target inside enemy ground static defence reach (unless the target is
 * itself a structure that fires on ground units), and the current target keeps a stickiness bonus.
 *
 * <p>Tiers, highest first:
 * <ul>
 *   <li>CRITICAL: anything that can attack the attacker's layer, workers that are attacking, and a Medic
 *       that is nearer than the nearest such candidate still open to the attacker (the nearest one when all
 *       are saturated) or is within its seek range of an injured biological candidate it can heal</li>
 *   <li>ELEVATED: other workers</li>
 *   <li>NORMAL: mobile units that cannot attack the attacker's layer</li>
 *   <li>LOW: buildings, including hostile buildings that cannot attack the attacker's layer</li>
 * </ul>
 *
 * <p>A target is saturated for a melee attacker once {@link #meleeCap} other melee attackers, from any fight squad,
 * hold it in the frame's {@link TargetLedger}.
 */
public final class TargetScorer {

    private static final double CURRENT_TARGET_BONUS = 1.2;
    private static final double GROUND_DEFENSE_FACTOR = 0.5;
    private static final int MELEE_MAX_RANGE = 31;

    public enum Priority {
        LOW,
        NORMAL,
        ELEVATED,
        CRITICAL
    }

    /**
     * Why a candidate was given its tier.
     */
    public enum Reason {
        THREAT(Priority.CRITICAL),
        MEAN_WORKER(Priority.CRITICAL),
        MEDIC_NEARER(Priority.CRITICAL),
        MEDIC_HEALING(Priority.CRITICAL),
        WORKER(Priority.ELEVATED),
        UNARMED(Priority.NORMAL),
        BUILDING(Priority.LOW);

        private final Priority priority;

        Reason(Priority priority) {
            this.priority = priority;
        }

        public Priority priority() {
            return priority;
        }
    }

    private TargetScorer() {
    }

    /**
     * Selects against an empty ledger: no squad load and no static defence penalty.
     *
     * @return the chosen target with the tier it was assigned, or null when there are no candidates
     */
    public static Selection selectTarget(Unit attacker, List<Unit> candidates, Unit currentTarget) {
        return selectTarget(attacker, candidates, currentTarget, TargetLedger.empty(), "");
    }

    /**
     * @param ledger the frame's melee assignments across every fight squad; it is read, not updated
     * @param squadId id of the squad the attacker fights in, carried on the selection for telemetry
     * @return the chosen target with the tier it was assigned, or null when there are no candidates
     */
    public static Selection selectTarget(Unit attacker, List<Unit> candidates, Unit currentTarget,
                                         TargetLedger ledger, String squadId) {
        if (candidates.isEmpty()) {
            return null;
        }

        boolean attackerIsFlying = attacker.isFlying();
        UnitType attackerType = attacker.getType();

        List<Candidate> scored = new ArrayList<>(candidates.size());
        for (Unit candidate : candidates) {
            scored.add(toCandidate(attacker, attackerType, candidate, currentTarget, candidates, ledger));
        }

        int best = selectIndex(attackerIsFlying, scored);
        Candidate chosen = scored.get(best);
        Reason reason = reasonAt(attackerIsFlying, scored, best);
        return new Selection(candidates.get(best), reason.priority(), candidates.size(), reason,
                chosen.assignedMelee(), chosen.saturated(), squadId, false);
    }

    /**
     * @return index of the best candidate: highest tier first, then unsaturated before saturated, then
     *     highest within-tier score. Ties keep the earlier candidate.
     */
    static int selectIndex(boolean attackerIsFlying, List<Candidate> candidates) {
        int nearestThreat = nearestThreatDistance(attackerIsFlying, candidates);
        int bestIndex = -1;
        Priority bestPriority = null;
        boolean bestSaturated = false;
        double bestScore = -1;

        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            Priority priority = candidate.reason(attackerIsFlying, nearestThreat).priority();
            boolean saturated = candidate.saturated();
            double score = candidate.score(attackerIsFlying);

            if (bestPriority == null || priority.ordinal() > bestPriority.ordinal()
                    || priority == bestPriority && bestSaturated && !saturated
                    || priority == bestPriority && bestSaturated == saturated && score > bestScore) {
                bestIndex = i;
                bestPriority = priority;
                bestSaturated = saturated;
                bestScore = score;
            }
        }

        return bestIndex;
    }

    /**
     * @return the reason the candidate at the index was given its tier among these candidates
     */
    static Reason reasonAt(boolean attackerIsFlying, List<Candidate> candidates, int index) {
        return candidates.get(index).reason(attackerIsFlying, nearestThreatDistance(attackerIsFlying, candidates));
    }

    static Priority assignPriority(UnitType candidateType, boolean attackerIsFlying, boolean isMeanWorker) {
        return baseReason(candidateType, attackerIsFlying, isMeanWorker).priority();
    }

    /**
     * The reason a candidate earns on its own, before any Medic promotion.
     */
    static Reason baseReason(UnitType candidateType, boolean attackerIsFlying, boolean isMeanWorker) {
        if (candidateType.isBuilding()) {
            if (Filter.isHostileBuilding(candidateType) && canAttackType(candidateType, attackerIsFlying)) {
                return Reason.THREAT;
            }
            return Reason.BUILDING;
        }

        if (Filter.isWorkerType(candidateType)) {
            return isMeanWorker ? Reason.MEAN_WORKER : Reason.WORKER;
        }

        return canAttackType(candidateType, attackerIsFlying) ? Reason.THREAT : Reason.UNARMED;
    }

    /**
     * Whether an attacker fights in melee: it has a ground weapon that reaches less than one tile.
     */
    public static boolean isMelee(UnitType attackerType) {
        WeaponType weapon = attackerType.groundWeapon();
        return weapon != null && weapon != WeaponType.None && weapon.maxRange() <= MELEE_MAX_RANGE;
    }

    /**
     * How many melee attackers of one type fit around a target: the perimeter of the target's footprint grown
     * by the attacker's larger dimension, divided by that dimension.
     *
     * @return the number of melee attackers past which the target is saturated, at least 1
     */
    public static int meleeCap(UnitType targetType, UnitType attackerType) {
        int attackerSize = Math.max(1, Math.max(attackerType.width(), attackerType.height()));
        int perimeter = 2 * (targetType.width() + attackerSize) + 2 * (targetType.height() + attackerSize);
        return Math.max(1, perimeter / attackerSize);
    }

    /**
     * Whether a Medic at a given distance from a biological unit is supporting it: the patient is one the
     * combat sim treats as Medic supported, it is below full hit points, and it is within the Medic's seek
     * range. JBWAPI gives healing no weapon range, so the seek range, the distance a Medic acquires heal
     * targets at, stands in for it.
     */
    static boolean supportsInjuredBio(UnitType patientType, int patientHitPoints, int distance) {
        return HorizonCombatSimulator.isMedicSupported(patientType)
                && patientHitPoints < patientType.maxHitPoints()
                && distance <= UnitType.Terran_Medic.seekRange();
    }

    static boolean canAttackType(UnitType unitType, boolean targetIsFlying) {
        if (unitType == UnitType.Terran_Bunker) return true;
        if (unitType == UnitType.Protoss_Carrier) return true;
        WeaponType weapon = targetIsFlying ? unitType.airWeapon() : unitType.groundWeapon();
        return weapon != null && weapon != WeaponType.None;
    }

    /**
     * @return distance to the nearest candidate whose own reason is CRITICAL and that is not saturated for the
     *     attacker, or to the nearest CRITICAL candidate when every one is saturated, or Integer.MAX_VALUE when
     *     there is none
     */
    private static int nearestThreatDistance(boolean attackerIsFlying, List<Candidate> candidates) {
        int nearest = Integer.MAX_VALUE;
        int nearestOpen = Integer.MAX_VALUE;
        for (Candidate candidate : candidates) {
            if (candidate.baseReason(attackerIsFlying).priority() == Priority.CRITICAL) {
                nearest = Math.min(nearest, candidate.distance);
                if (!candidate.saturated()) {
                    nearestOpen = Math.min(nearestOpen, candidate.distance);
                }
            }
        }
        return nearestOpen != Integer.MAX_VALUE ? nearestOpen : nearest;
    }

    private static Candidate toCandidate(Unit attacker, UnitType attackerType, Unit candidate, Unit currentTarget,
                                         List<Unit> candidates, TargetLedger ledger) {
        UnitType type = candidate.getType();
        double hpFraction = (double) (candidate.getHitPoints() + candidate.getShields())
                / (type.maxHitPoints() + type.maxShields());
        boolean isCurrent = currentTarget != null && candidate.getID() == currentTarget.getID();
        Candidate base = new Candidate(type, attacker.getDistance(candidate), hpFraction, isCurrent,
                Filter.isMeanWorker(candidate));
        return withLedger(base, attacker.getID(), attackerType, attacker.isFlying(), candidate.getID(),
                candidate::getPosition, ledger).withHealingInjuredBio(type == UnitType.Terran_Medic && healsAnyCandidate(candidate, candidates));
    }

    /**
     * Applies the frame's ledger to a candidate: the melee load other attackers put on it with the attacker's cap,
     * and, for a
     * ground attacker, whether it stands inside enemy ground static defence. The position is read only when that
     * check is needed.
     */
    static Candidate withLedger(Candidate candidate, int attackerId, UnitType attackerType, boolean attackerIsFlying,
                                int targetId, Supplier<Position> position, TargetLedger ledger) {
        boolean covered = !attackerIsFlying && ledger.hasGroundDefense()
                && isPenalizedByGroundDefense(candidate.type()) && ledger.insideGroundDefense(position.get());
        return candidate.withMeleeLoad(ledger.meleeAssignedExcept(targetId, attackerId),
                loadCap(attackerType, candidate.type()))
                .withGroundDefense(covered);
    }

    /**
     * Whether a candidate standing inside enemy ground static defence has its score reduced: every candidate but
     * a structure that itself fires on ground units, which stands inside its own zone.
     */
    static boolean isPenalizedByGroundDefense(UnitType type) {
        return !type.isBuilding() || !canAttackType(type, false);
    }

    /**
     * @return {@link #meleeCap} for a melee attacker, or Integer.MAX_VALUE, never saturated, for any other
     */
    static int loadCap(UnitType attackerType, UnitType targetType) {
        return isMelee(attackerType) ? meleeCap(targetType, attackerType) : Integer.MAX_VALUE;
    }

    private static boolean healsAnyCandidate(Unit medic, List<Unit> candidates) {
        for (Unit patient : candidates) {
            if (patient.getID() != medic.getID()
                    && supportsInjuredBio(patient.getType(), patient.getHitPoints(), medic.getDistance(patient))) {
                return true;
            }
        }
        return false;
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
        private int assignedMelee;
        private int meleeCap = Integer.MAX_VALUE;
        private boolean inGroundDefense;
        private boolean healingInjuredBio;

        Candidate(UnitType type, int distance, double hpFraction, boolean currentTarget, boolean meanWorker) {
            this.type = type;
            this.distance = distance;
            this.hpFraction = hpFraction;
            this.currentTarget = currentTarget;
            this.meanWorker = meanWorker;
        }

        /**
         * @param assigned other melee attackers holding this candidate in the frame's ledger
         * @param cap load at which the candidate is saturated, see {@link #loadCap}
         */
        Candidate withMeleeLoad(int assigned, int cap) {
            this.assignedMelee = assigned;
            this.meleeCap = cap;
            return this;
        }

        Candidate withGroundDefense(boolean covered) {
            this.inGroundDefense = covered;
            return this;
        }

        Candidate withHealingInjuredBio(boolean healing) {
            this.healingInjuredBio = healing;
            return this;
        }

        UnitType type() {
            return type;
        }

        int assignedMelee() {
            return assignedMelee;
        }

        int meleeCap() {
            return meleeCap;
        }

        boolean inGroundDefense() {
            return inGroundDefense;
        }

        boolean saturated() {
            return assignedMelee >= meleeCap;
        }

        Reason baseReason(boolean attackerIsFlying) {
            return TargetScorer.baseReason(type, attackerIsFlying, meanWorker);
        }

        /**
         * @param nearestThreat distance from {@link TargetScorer#nearestThreatDistance}
         */
        Reason reason(boolean attackerIsFlying, int nearestThreat) {
            Reason base = baseReason(attackerIsFlying);
            if (type != UnitType.Terran_Medic || base.priority() == Priority.CRITICAL) {
                return base;
            }
            if (healingInjuredBio) {
                return Reason.MEDIC_HEALING;
            }
            if (nearestThreat != Integer.MAX_VALUE && distance < nearestThreat) {
                return Reason.MEDIC_NEARER;
            }
            return base;
        }

        double score(boolean attackerIsFlying) {
            int clamped = Math.max(distance, 1);
            double injuryBonus = 1.0 + 0.5 * (1.0 - hpFraction);
            double defenseFactor = !attackerIsFlying && inGroundDefense ? GROUND_DEFENSE_FACTOR : 1.0;
            double currentTargetBonus = currentTarget ? CURRENT_TARGET_BONUS : 1.0;
            return injuryBonus * defenseFactor * currentTargetBonus / clamped;
        }
    }

    /**
     * A chosen target, the tier it was assigned and why, how many candidates it was chosen from, how many
     * other melee attackers already held it, whether that load had saturated it, and whether it was found only
     * after widening the candidates past the targeting radius.
     */
    public static final class Selection {
        private final Unit target;
        private final Priority priority;
        private final int candidateCount;
        private final Reason reason;
        private final int assignedCount;
        private final boolean saturated;
        private final String squadId;
        private final boolean widened;

        public Selection(Unit target, Priority priority, int candidateCount) {
            this(target, priority, candidateCount, null, 0, false, "", false);
        }

        public Selection(Unit target, Priority priority, int candidateCount, Reason reason, int assignedCount,
                         boolean saturated, String squadId, boolean widened) {
            this.target = target;
            this.priority = priority;
            this.candidateCount = candidateCount;
            this.reason = reason;
            this.assignedCount = assignedCount;
            this.saturated = saturated;
            this.squadId = squadId;
            this.widened = widened;
        }

        /**
         * @return this selection, marked as made from candidates widened past the targeting radius
         */
        public Selection asWidened() {
            return new Selection(target, priority, candidateCount, reason, assignedCount, saturated, squadId, true);
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

        public Reason getReason() {
            return reason;
        }

        /**
         * @return other melee attackers, from any fight squad, holding this target in the frame's ledger
         */
        public int getAssignedCount() {
            return assignedCount;
        }

        public boolean isSaturated() {
            return saturated;
        }

        /**
         * @return the id of the squad the attacker was targeted for, empty outside one
         */
        public String getSquadId() {
            return squadId;
        }

        public boolean isWidened() {
            return widened;
        }
    }
}
