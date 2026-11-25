package unit.squad;

import bwapi.Position;
import bwapi.UnitType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Which of our unit types take a Protoss Interceptor as a fight target.
 *
 * <p>A Carrier rebuilds its Interceptors, so a unit that spends itself on one gains nothing while the Carrier
 * lives. Scourge never take one: each Scourge is a single hit, owed to the Carrier. Hydralisks and Mutalisks take
 * one last: never while a Carrier is among their candidates, see {@link #admitted}, and never while anything else
 * is among the candidates they pick from, see {@link #preferred}. Left with Interceptors only, they do not attack
 * the one they are given, see {@link #yields}, but attack-move to where the Carrier is, see
 * {@link #attackMovePoint}, and shoot what comes into range on the way. They do the same for a pick beyond their
 * targeting reach while Interceptors are within it, see {@link #attackMoves}.
 */
public final class InterceptorTargeting {

    private InterceptorTargeting() {
    }

    /**
     * Whether an attacker of the given type never takes an Interceptor as a fight target.
     *
     * @param attacker the attacking unit's type
     * @return true for Scourge
     */
    static boolean ignoresInterceptors(UnitType attacker) {
        return attacker == UnitType.Zerg_Scourge;
    }

    /**
     * Whether an attacker of the given type takes an Interceptor only when it has no other candidate.
     *
     * @param attacker the attacking unit's type
     * @return true for Hydralisks and Mutalisks
     */
    static boolean takesInterceptorsLast(UnitType attacker) {
        return attacker == UnitType.Zerg_Hydralisk || attacker == UnitType.Zerg_Mutalisk;
    }

    /**
     * Whether an attacker may be given a target of the given type, as far as Interceptors go. Every target that is
     * not an Interceptor is admitted for every attacker.
     *
     * @param attacker the attacking unit's type
     * @param target the candidate target's type
     * @return false when the target is an Interceptor and the attacker ignores Interceptors
     */
    static boolean admits(UnitType attacker, UnitType target) {
        return target != UnitType.Protoss_Interceptor || !ignoresInterceptors(attacker);
    }

    /**
     * Whether an attacker given a target of this type does not attack it, and attack-moves instead.
     *
     * @param attacker the attacking unit's type
     * @param target the target's type
     * @return true for an Interceptor given to an attacker that takes Interceptors last
     */
    public static boolean yields(UnitType attacker, UnitType target) {
        return target == UnitType.Protoss_Interceptor && takesInterceptorsLast(attacker);
    }

    /**
     * Whether an attacker attack-moves instead of attacking its pick: an attacker that takes Interceptors last,
     * picking an Interceptor, or picking a target beyond its targeting reach while an Interceptor is within it, so
     * it does not walk off under their fire without shooting back.
     *
     * @param attacker the attacking unit's type
     * @param target the picked target's type
     * @param targetInReach true when the pick is within the attacker's targeting reach
     * @param interceptorInReach true when an Interceptor is within the attacker's targeting reach
     * @return true when the pick is issued as an attack-move
     */
    static boolean attackMoves(UnitType attacker, UnitType target, boolean targetInReach,
                               boolean interceptorInReach) {
        return yields(attacker, target)
                || takesInterceptorsLast(attacker) && !targetInReach && interceptorInReach;
    }

    /**
     * The candidates an attacker keeps, in their original order: every candidate, less the Interceptors for an
     * attacker that ignores them, and less the Interceptors for an attacker that takes them last when a Carrier is
     * among the candidates.
     *
     * @param attacker the attacking unit's type
     * @param candidates the enemies the attacker could otherwise be given
     * @param typeOf reads a candidate's unit type
     * @param <T> the candidate representation
     * @return the admitted candidates
     */
    static <T> List<T> admitted(UnitType attacker, List<T> candidates, Function<T, UnitType> typeOf) {
        boolean carrier = false;
        for (T candidate : candidates) {
            carrier = carrier || typeOf.apply(candidate) == UnitType.Protoss_Carrier;
        }
        if (ignoresInterceptors(attacker) || carrier && takesInterceptorsLast(attacker)) {
            return withoutInterceptors(candidates, typeOf);
        }
        return new ArrayList<>(candidates);
    }

    /**
     * The candidates an attacker picks its target from, in their original order: for an attacker that takes
     * Interceptors last, the candidates less the Interceptors whenever any other candidate remains. Every other
     * attacker keeps them all.
     *
     * @param attacker the attacking unit's type
     * @param candidates the enemies the attacker would pick from
     * @param typeOf reads a candidate's unit type
     * @param <T> the candidate representation
     * @return the candidates to pick from, Interceptors only when nothing else is among them
     */
    static <T> List<T> preferred(UnitType attacker, List<T> candidates, Function<T, UnitType> typeOf) {
        if (!takesInterceptorsLast(attacker)) {
            return candidates;
        }
        List<T> others = withoutInterceptors(candidates, typeOf);
        return others.isEmpty() ? candidates : others;
    }

    /**
     * Where an attacker that yields to an Interceptor attack-moves: the nearest position a Carrier is tracked at,
     * or the squad's objective when no Carrier's position is known.
     *
     * @param from the attacker's position
     * @param carriers the current or last known position of every living Carrier we track
     * @param objective where the squad heads when it has no enemy to fight, read only when no Carrier is tracked
     * @return the point to attack-move to
     */
    static Position attackMovePoint(Position from, Collection<Position> carriers, Supplier<Position> objective) {
        Position nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Position carrier : carriers) {
            double distance = from.getDistance(carrier);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = carrier;
            }
        }
        return nearest != null ? nearest : objective.get();
    }

    private static <T> List<T> withoutInterceptors(List<T> candidates, Function<T, UnitType> typeOf) {
        List<T> kept = new ArrayList<>();
        for (T candidate : candidates) {
            if (typeOf.apply(candidate) != UnitType.Protoss_Interceptor) {
                kept.add(candidate);
            }
        }
        return kept;
    }
}
