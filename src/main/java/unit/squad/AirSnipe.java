package unit.squad;

import bwapi.Position;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import bwapi.WeaponType;
import lombok.Getter;
import unit.squad.horizon.UnitStrength;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the targets a flock kills in one volley, as static functions over plain values.
 *
 * <p>A volley is every Mutalisk's Glave Wurm hit landing at once. Its alpha against a target is the Mutalisks that
 * fire times the weapon's damage factor times the damage one hit deals: the weapon's damage, upgrades the caller
 * passes in, less the target's armor, scaled by the weapon's damage type against the target's size, and never below
 * one. Only primary hits count. JBWAPI carries no bounce falloff for the Glave Wurm, so the damage its bounces deal to
 * further targets is left out, which under-reads the alpha. A target is a snipe when its hit points plus shields do
 * not exceed the alpha.
 *
 * <p>Snipes are ranked by the kills the flock makes per volley, the targets needing the fewest Mutalisks first so
 * the most of them die together, then by kill value, the mineral plus gas price of the target.
 */
public final class AirSnipe {

    private AirSnipe() {
    }

    /**
     * A target a volley could kill.
     */
    @Getter
    public static final class Candidate {
        private final int id;
        private final UnitType type;
        private final Position position;
        private final int hitPoints;
        private final int perHit;

        /**
         * @param id enemy unit id
         * @param type its type
         * @param position its position
         * @param hitPoints its hit points plus shields
         * @param perHit the damage one Glave Wurm hit deals to it, see {@link #perHit}
         */
        public Candidate(int id, UnitType type, Position position, int hitPoints, int perHit) {
            this.id = id;
            this.type = type;
            this.position = position;
            this.hitPoints = hitPoints;
            this.perHit = perHit;
        }

        /**
         * @return the mineral plus gas price of the target
         */
        public int value() {
            return type.mineralPrice() + type.gasPrice();
        }
    }

    /**
     * Mutalisks assigned to kill one target.
     */
    @Getter
    public static final class Assignment {
        private final Candidate target;
        private final int mutas;

        Assignment(Candidate target, int mutas) {
            this.target = target;
            this.mutas = mutas;
        }
    }

    /**
     * The damage one Glave Wurm hit deals to a target.
     *
     * @param damage the weapon's damage per hit for us, upgrades counted
     * @param armor the target's armor
     * @param weapon the weapon, for its damage type
     * @param size the target's size
     * @return the damage per hit, at least 1
     */
    public static int perHit(int damage, int armor, WeaponType weapon, UnitSizeType size) {
        double dealt = (damage - armor) * UnitStrength.effectiveness(weapon.damageType(), size);
        return (int) Math.max(1, dealt);
    }

    /**
     * The damage a volley of Mutalisks deals to one target.
     *
     * @param mutas Mutalisks that fire
     * @param damageFactor the weapon's hits per attack, see {@link WeaponType#damageFactor}
     * @param perHit the damage one hit deals to the target
     * @return the volley's damage
     */
    public static int alpha(int mutas, int damageFactor, int perHit) {
        return Math.max(0, mutas) * damageFactor * perHit;
    }

    /**
     * Whether a volley kills a target.
     *
     * @param hitPoints the target's hit points plus shields
     * @param alpha the volley's damage
     * @return true when the alpha covers the pool
     */
    public static boolean isSnipe(int hitPoints, int alpha) {
        return hitPoints <= alpha;
    }

    /**
     * Mutalisks a target needs to die in one volley.
     *
     * @param target the target
     * @param damageFactor the weapon's hits per attack
     * @return the Mutalisks needed
     */
    static int mutasNeeded(Candidate target, int damageFactor) {
        int perMuta = Math.max(1, damageFactor * target.getPerHit());
        return Math.max(1, (target.getHitPoints() + perMuta - 1) / perMuta);
    }

    /**
     * The volley plan: of the candidates the flock kills in a volley, those it kills most of per volley, fewest
     * Mutalisks needed first, then the highest kill value, until the Mutalisks run out.
     *
     * @param candidates the targets in reach
     * @param mutas Mutalisks that fire
     * @param damageFactor the weapon's hits per attack
     * @return the assignments, in the order the plan takes them
     */
    public static List<Assignment> plan(Collection<Candidate> candidates, int mutas, int damageFactor) {
        List<Candidate> snipes = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (isSnipe(candidate.getHitPoints(), alpha(mutas, damageFactor, candidate.getPerHit()))) {
                snipes.add(candidate);
            }
        }
        snipes.sort(Comparator.<Candidate>comparingInt(candidate -> mutasNeeded(candidate, damageFactor))
                .thenComparing(Comparator.comparingInt(Candidate::value).reversed())
                .thenComparingInt(Candidate::getId));
        List<Assignment> plan = new ArrayList<>();
        int left = mutas;
        for (Candidate snipe : snipes) {
            int needed = mutasNeeded(snipe, damageFactor);
            if (needed <= left) {
                plan.add(new Assignment(snipe, needed));
                left -= needed;
            }
        }
        return plan;
    }

    /**
     * Gives each planned target the Mutalisks nearest it that no earlier target took.
     *
     * @param plan the volley plan, see {@link #plan}
     * @param mutas positions of the Mutalisks that fire, by unit id
     * @return the target id each assigned Mutalisk fires on, by Mutalisk id
     */
    public static Map<Integer, Integer> assign(List<Assignment> plan, Map<Integer, Position> mutas) {
        Map<Integer, Integer> targets = new HashMap<>();
        Set<Integer> taken = new HashSet<>();
        for (Assignment assignment : plan) {
            List<Integer> free = new ArrayList<>();
            for (Integer id : mutas.keySet()) {
                if (!taken.contains(id)) {
                    free.add(id);
                }
            }
            Position at = assignment.getTarget().getPosition();
            free.sort(Comparator.<Integer>comparingDouble(id -> mutas.get(id).getDistance(at))
                    .thenComparingInt(id -> id));
            for (int i = 0; i < assignment.getMutas() && i < free.size(); i++) {
                targets.put(free.get(i), assignment.getTarget().getId());
                taken.add(free.get(i));
            }
        }
        return targets;
    }
}
