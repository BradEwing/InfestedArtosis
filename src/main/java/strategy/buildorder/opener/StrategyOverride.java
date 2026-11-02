package strategy.buildorder.opener;

import bwapi.Race;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads the strategy override, IA_STRATEGY_OVERRIDE: one build order name, or a comma-separated list of names so a
 * single run can lock one build per opponent race, for example {@code 1HatchSpire,2HatchMuta,3HatchMuta}. Names are
 * tried in the order listed and a name that is not offered is skipped.
 */
public final class StrategyOverride {
    private StrategyOverride() {
    }

    /**
     * @param override the override, or null
     * @return its names in the order listed, trimmed, without empty entries; empty when the override is null
     */
    public static List<String> names(String override) {
        List<String> names = new ArrayList<>();
        if (override == null) {
            return names;
        }
        for (String name : override.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }

    /**
     * @param override the override, or null
     * @param name a build order name
     * @return true when the override lists the name
     */
    public static boolean lists(String override, String name) {
        return names(override).contains(name);
    }

    /**
     * The first listed build order among the candidates.
     *
     * @param override the override, or null
     * @param candidates the build orders on offer
     * @param byName looks a build order up by name, null when there is none
     * @return the build order, or null when the override lists none of the candidates
     */
    public static BuildOrder pick(String override, Set<BuildOrder> candidates, Function<String, BuildOrder> byName) {
        for (String name : names(override)) {
            BuildOrder buildOrder = byName.apply(name);
            if (buildOrder != null && candidates.contains(buildOrder)) {
                return buildOrder;
            }
        }
        return null;
    }

    /**
     * The strategy forced at game start. A single name forces that build order whatever the opponent's race, as long
     * as it is not an opener. A list forces the first listed build order that an opener transitions into against the
     * opponent's race, see {@link OpenerTransitions#forRace}, so a Random opponent, whose race is not yet known, gets
     * none.
     *
     * @param override the override, or null
     * @param opponentRace the opponent's race at game start
     * @param byName looks a build order up by name, null when there is none
     * @return the build order, or null when none is forced
     */
    public static BuildOrder atGameStart(String override, Race opponentRace, Function<String, BuildOrder> byName) {
        List<String> names = names(override);
        if (names.size() == 1) {
            BuildOrder forced = byName.apply(names.get(0));
            return forced != null && !forced.isOpener() ? forced : null;
        }
        return pick(override, OpenerTransitions.forRace(opponentRace, false, true), byName);
    }
}
