package info;

import java.util.ArrayList;
import java.util.List;

/**
 * Build orders entered through transitions during the current game, in game order. The joined
 * chain is the value written to the build_order column of the learning and telemetry game rows,
 * so every writer emits the same string.
 */
public final class BuildOrderChain {
    /**
     * Upper bound on chain length. A pair of build orders that transition into one another would
     * otherwise append every frame and grow the recorded row without limit.
     */
    static final int MAX_ENTRIES = 8;

    private final List<String> entries = new ArrayList<>();

    /**
     * Appends a build order name, collapsing consecutive repeats so a transition that re-fires
     * before the new build order activates cannot duplicate the entry.
     */
    public void add(String name) {
        if (name == null || name.isEmpty() || entries.size() >= MAX_ENTRIES) {
            return;
        }
        if (!entries.isEmpty() && entries.get(entries.size() - 1).equals(name)) {
            return;
        }
        entries.add(name);
    }

    public String join() {
        return String.join(";", entries);
    }

    /**
     * The joined chain, or the fallback when nothing was transitioned into, so every writer emits
     * its build_order value from this one rule.
     */
    public String joinOrElse(String fallback) {
        return entries.isEmpty() ? fallback : join();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
