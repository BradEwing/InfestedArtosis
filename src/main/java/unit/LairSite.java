package unit;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Picks the Hatchery a Lair plan morphs on.
 *
 * <p>Among the free Hatcheries, the one standing on the main base location wins. The rest follow by
 * straight-line distance to the main base location, so a macro Hatchery inside the main is taken
 * before the natural, and unit id breaks any remaining tie. The result never depends on the
 * iteration order of the Hatchery collection.
 */
final class LairSite {
    private LairSite() {
    }

    static <T> T choose(Collection<T> hatcheries,
                        Predicate<T> isFree,
                        Predicate<T> isMainBase,
                        ToDoubleFunction<T> distanceToMainBase,
                        ToIntFunction<T> unitId) {
        Comparator<T> order = Comparator.<T, Boolean>comparing(h -> !isMainBase.test(h))
                .thenComparingDouble(distanceToMainBase)
                .thenComparingInt(unitId);
        return hatcheries.stream()
                .filter(isFree)
                .min(order)
                .orElse(null);
    }
}
