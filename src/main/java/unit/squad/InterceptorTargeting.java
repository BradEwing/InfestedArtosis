package unit.squad;

import bwapi.UnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Which of our unit types take a Protoss Interceptor as a fight target.
 *
 * <p>A Carrier rebuilds its Interceptors, so a unit that spends itself on one gains nothing while the Carrier
 * lives. Scourge never take one: each Scourge is a single hit, owed to the Carrier.
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
     * The candidates an attacker keeps once the Interceptors it ignores are left out, in their original order.
     *
     * @param attacker the attacking unit's type
     * @param candidates the enemies the attacker could otherwise be given
     * @param typeOf reads a candidate's unit type
     * @param <T> the candidate representation
     * @return the admitted candidates
     */
    static <T> List<T> admitted(UnitType attacker, List<T> candidates, Function<T, UnitType> typeOf) {
        List<T> kept = new ArrayList<>();
        for (T candidate : candidates) {
            if (admits(attacker, typeOf.apply(candidate))) {
                kept.add(candidate);
            }
        }
        return kept;
    }
}
