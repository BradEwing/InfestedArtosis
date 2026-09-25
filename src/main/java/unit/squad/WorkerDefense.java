package unit.squad;

import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Decides how many gatherers a base under attack commits to worker defence.
 *
 * <p>The rule has a losing branch: the defence is first simulated with every defender already assigned plus
 * every candidate. If even that full commitment cannot win, nobody is pulled and the assigned defenders are
 * released to mine. Otherwise candidates are added one at a time until the defence clears the threat.
 */
public final class WorkerDefense {

    /**
     * Most gatherers that may be pulled from another base, and only when no enemy combat unit is among the
     * threats. Workers crossing the map into an army die on the approach.
     */
    public static final int CROSS_BASE_DEFENDER_CAP = 2;

    /**
     * Frames a base waits after abandoning its defence before it may pull again, so a verdict at the margin
     * does not flip drones between mining and fighting every frame.
     */
    public static final int ABANDON_HOLD_FRAMES = 72;

    static final int MIN_GATHERERS_TO_PULL = 3;

    private WorkerDefense() {
    }

    @Value
    public static class Outcome<T> {
        boolean abandoned;
        List<T> pulled;
        List<T> released;
    }

    /**
     * Chooses the gatherers that may be pulled into the defence of a base.
     *
     * <p>A base defends with its own gatherers. A base with none may draw on another base's gatherers, capped
     * at {@link #CROSS_BASE_DEFENDER_CAP} and at zero when an enemy combat unit threatens. A source base with
     * fewer than {@link #MIN_GATHERERS_TO_PULL} gatherers gives none. The minimum counts every gatherer at the
     * source base, and only the pullable ones among them are offered, so a gatherer held back does not stop the
     * others from defending.
     *
     * @param ownGatherers gatherers mining at the threatened base, closest first
     * @param otherGatherers gatherers mining at the fallback base, closest first
     * @param combatUnitThreat true if a mobile enemy ground combat unit is among the threats
     * @param uncapped true to lift the cross base cap, as a cannon rush defence does
     * @param pullable whether a gatherer may be offered at all
     * @return candidates in pull order
     */
    public static <T> List<T> candidates(List<T> ownGatherers, List<T> otherGatherers, boolean combatUnitThreat,
                                         boolean uncapped, Predicate<T> pullable) {
        if (!ownGatherers.isEmpty()) {
            return ownGatherers.size() < MIN_GATHERERS_TO_PULL
                    ? Collections.emptyList()
                    : pullableOf(ownGatherers, pullable);
        }
        if (otherGatherers.size() < MIN_GATHERERS_TO_PULL) {
            return Collections.emptyList();
        }
        List<T> pullableOther = pullableOf(otherGatherers, pullable);
        if (uncapped) {
            return pullableOther;
        }
        int cap = combatUnitThreat ? 0 : CROSS_BASE_DEFENDER_CAP;
        return new ArrayList<>(pullableOther.subList(0, Math.min(cap, pullableOther.size())));
    }

    private static <T> List<T> pullableOf(List<T> gatherers, Predicate<T> pullable) {
        return gatherers.stream().filter(pullable).collect(Collectors.toList());
    }

    /**
     * Decides which candidates join the defence and whether the assigned defenders stay.
     *
     * <p>With no members and no candidates there is nothing to decide, and the outcome is neither an abandon
     * nor a pull.
     *
     * @param members defenders already assigned to the base
     * @param candidates gatherers that may be pulled, in pull order
     * @param wins true if the given defenders win the simulated fight
     * @param clears true if the given defenders are enough to stop pulling, at least as strict as wins
     * @return the outcome: abandoned with every member released, or the candidates to pull
     */
    public static <T> Outcome<T> decide(List<T> members, List<T> candidates, Predicate<List<T>> wins,
                                        Predicate<List<T>> clears) {
        List<T> committed = new ArrayList<>(members);
        committed.addAll(candidates);
        if (committed.isEmpty()) {
            return new Outcome<>(false, Collections.emptyList(), Collections.emptyList());
        }
        if (!wins.test(committed)) {
            return new Outcome<>(true, Collections.emptyList(), new ArrayList<>(members));
        }

        List<T> defenders = new ArrayList<>(members);
        List<T> pulled = new ArrayList<>();
        for (T candidate : candidates) {
            if (clears.test(defenders)) {
                break;
            }
            defenders.add(candidate);
            pulled.add(candidate);
        }
        return new Outcome<>(false, pulled, Collections.emptyList());
    }

    /**
     * Returns true if the simulated defence wins: every enemy is gone, or at least the threshold fraction of
     * defenders survive.
     */
    public static boolean defenceWins(int defenders, int defenderSurvivors, int enemySurvivors, double threshold) {
        if (enemySurvivors == 0) {
            return true;
        }
        if (defenders == 0 || defenderSurvivors == 0) {
            return false;
        }
        return (double) defenderSurvivors / defenders >= threshold;
    }

    /**
     * Returns true while a base that abandoned its defence may not pull again.
     */
    public static boolean abandonHeld(Integer holdUntilFrame, int frame) {
        return holdUntilFrame != null && frame < holdUntilFrame;
    }
}
