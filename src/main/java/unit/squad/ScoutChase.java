package unit.squad;

import bwapi.UnitType;
import util.Filter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Caps how many of our units chase one enemy scout, across every squad.
 *
 * <p>The ledger maps a scout's unit id to the ids of the attackers holding it. It is rebuilt at the start of
 * each squad frame from the targets our units already hold, keeping the closest claimants up to each one's
 * {@link #chaserCap}, and then filled in as targets are assigned during the frame. An attacker that is not
 * admitted drops the scout from its candidates before the proximity filter, so it falls through to the
 * threats beyond the scout.
 */
public final class ScoutChase {

    /**
     * Manhattan tile radius around an enemy base inside which a worker counts as mining at home rather than
     * scouting.
     */
    static final int ENEMY_BASE_TILE_RADIUS = 12;

    private final Map<Integer, Set<Integer>> holders = new HashMap<>();

    /**
     * What a unit the cap turned away from a scout does next.
     */
    enum Excess {
        /** Take one of the candidates left through the normal target path. */
        FIGHT,
        /** Rally to the threatened base. */
        DEFEND,
        /** Rally on its squad and keep the squad's goal. */
        FOLLOW_SQUAD
    }

    /**
     * An attacker currently targeting a scout, with its distance to that scout and the cap its speed allows.
     */
    static final class Claim {
        private final int scoutId;
        private final int attackerId;
        private final double distance;
        private final int cap;

        Claim(int scoutId, int attackerId, double distance, int cap) {
            this.scoutId = scoutId;
            this.attackerId = attackerId;
            this.distance = distance;
            this.cap = cap;
        }
    }

    /**
     * Returns true if a unit is a scout whose chasers are capped: a worker that is neither attacking nor
     * constructing, or an Overlord or Observer, standing away from every enemy base.
     *
     * @param type the enemy unit's type
     * @param attacking true if the unit is attacking
     * @param constructing true if the unit is constructing
     * @param nearEnemyBase true if the unit stands within {@link #ENEMY_BASE_TILE_RADIUS} of an enemy base
     */
    static boolean isScout(UnitType type, boolean attacking, boolean constructing, boolean nearEnemyBase) {
        if (nearEnemyBase) {
            return false;
        }
        if (Filter.isWorkerType(type)) {
            return !attacking && !constructing;
        }
        return type == UnitType.Zerg_Overlord || type == UnitType.Protoss_Observer;
    }

    /**
     * @return how many units of the chaser's speed may hold one scout: 2 when the chaser is faster than the
     *     scout, else 1
     */
    static int chaserCap(double chaserTopSpeed, double scoutTopSpeed) {
        return chaserTopSpeed > scoutTopSpeed ? 2 : 1;
    }

    /**
     * Returns the candidates with every capped scout removed.
     *
     * @param candidates attackable enemies
     * @param capped true for a candidate that is a scout the attacker may not take
     */
    static <T> List<T> withoutCappedScouts(List<T> candidates, Predicate<T> capped) {
        List<T> kept = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (!capped.test(candidate)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    /**
     * Returns true if a unit the cap turned away from a scout should not simply take one of the candidates left:
     * none are left, or the nearest is beyond the squad's detection radius, where its squad's simulation has not
     * measured it.
     *
     * @param remaining candidates left after the cap
     * @param nearestRemainingDistance distance to the nearest of them
     * @param detectionRadius the squad's enemy detection radius
     */
    static boolean shouldDefend(int remaining, double nearestRemainingDistance, double detectionRadius) {
        return remaining == 0 || nearestRemainingDistance > detectionRadius;
    }

    /**
     * Chooses what a unit the cap turned away from a scout does. When {@link #shouldDefend} holds it defends only
     * if one of our bases is threatened. Otherwise it follows its squad when nothing is left to fight, or takes the
     * distant candidates left.
     *
     * @param remaining candidates left after the cap
     * @param nearestRemainingDistance distance to the nearest of them
     * @param detectionRadius the squad's enemy detection radius
     * @param baseThreatened true if a base or our natural holds an enemy mobile ground combat unit
     */
    static Excess excessAction(int remaining, double nearestRemainingDistance, double detectionRadius,
                               boolean baseThreatened) {
        if (!shouldDefend(remaining, nearestRemainingDistance, detectionRadius)) {
            return Excess.FIGHT;
        }
        if (baseThreatened) {
            return Excess.DEFEND;
        }
        return remaining == 0 ? Excess.FOLLOW_SQUAD : Excess.FIGHT;
    }

    /**
     * Rebuilds the ledger from the scouts our units already target. For each scout the claims are taken
     * closest first, and a claim is kept while the scout's holders number fewer than that claim's cap.
     */
    void beginFrame(List<Claim> claims) {
        holders.clear();
        List<Claim> sorted = new ArrayList<>(claims);
        sorted.sort(Comparator.comparingDouble(claim -> claim.distance));
        for (Claim claim : sorted) {
            Set<Integer> held = holders.computeIfAbsent(claim.scoutId, id -> new HashSet<>());
            if (held.size() < claim.cap) {
                held.add(claim.attackerId);
            }
        }
    }

    /**
     * @return true if the attacker already holds the scout, or the scout has fewer holders than the cap
     */
    boolean admits(int scoutId, int attackerId, int cap) {
        Set<Integer> held = holders.get(scoutId);
        if (held == null) {
            return cap > 0;
        }
        return held.contains(attackerId) || held.size() < cap;
    }

    /**
     * Records the attacker as a holder of the scout, releasing any other scout it held.
     */
    void claim(int scoutId, int attackerId) {
        release(attackerId);
        holders.computeIfAbsent(scoutId, id -> new HashSet<>()).add(attackerId);
    }

    /**
     * Frees every slot the attacker holds.
     */
    void release(int attackerId) {
        for (Set<Integer> held : holders.values()) {
            held.remove(attackerId);
        }
    }

    /**
     * Drops a scout's row, as when it dies.
     */
    void releaseScout(int scoutId) {
        holders.remove(scoutId);
    }

    /**
     * @return the number of attackers holding the scout
     */
    int holderCount(int scoutId) {
        Set<Integer> held = holders.get(scoutId);
        return held == null ? 0 : held.size();
    }
}
