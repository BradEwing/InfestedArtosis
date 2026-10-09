package learning;

import bwapi.Race;
import strategy.buildorder.SpeedlingP;
import strategy.buildorder.SpeedlingR;
import strategy.buildorder.SpeedlingT;
import strategy.buildorder.SpeedlingZ;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Maps build order names that older learning rows carry onto the builds that replaced them.
 *
 * <p>Rows written before the Speedling split name {@value #LEGACY_SPEEDLING}, which played every
 * race. The learning file is per opponent and race, so the race variant is the one for the race the
 * file holds, and a file for an Unknown race maps to {@link SpeedlingR}.
 */
public final class LegacyBuildOrderNames {

    public static final String LEGACY_SPEEDLING = "SpeedlingAllIn";

    private static final String CHAIN_SEPARATOR = ";";

    private LegacyBuildOrderNames() {
    }

    /**
     * @param raceName the race of the learning file, as {@code Race.toString()} writes it
     * @return the Speedling variant for that race
     */
    public static String speedlingFor(String raceName) {
        if (Race.Terran.toString().equals(raceName)) {
            return SpeedlingT.NAME;
        }
        if (Race.Protoss.toString().equals(raceName)) {
            return SpeedlingP.NAME;
        }
        if (Race.Zerg.toString().equals(raceName)) {
            return SpeedlingZ.NAME;
        }
        return SpeedlingR.NAME;
    }

    /**
     * @param name a single build order name
     * @param raceName the race of the learning file
     * @return the race variant when the name is the legacy Speedling, else the name unchanged
     */
    public static String resolve(String name, String raceName) {
        return LEGACY_SPEEDLING.equals(name) ? speedlingFor(raceName) : name;
    }

    /**
     * @param race the opponent's race
     * @return {@link #resolve(String, String)} for a {@link Race}
     */
    public static String resolve(String name, Race race) {
        return resolve(name, race.toString());
    }

    /**
     * @param fileRace the race of the learning file
     * @param rowRace the race recorded on the row, which is the resolved race for a Random opponent
     * @return the race that picks the variant: the row's race when it is Terran, Protoss or Zerg, else the file's
     */
    public static String variantRace(String fileRace, String rowRace) {
        if (Race.Terran.toString().equals(rowRace) || Race.Protoss.toString().equals(rowRace)
                || Race.Zerg.toString().equals(rowRace)) {
            return rowRace;
        }
        return fileRace;
    }

    /**
     * @param chain a build_order column value: build order names joined by semicolons
     * @param raceName the race of the learning file
     * @return the chain with every legacy Speedling segment replaced, null and empty chains unchanged
     */
    public static String resolveChain(String chain, String raceName) {
        if (chain == null || !chain.contains(LEGACY_SPEEDLING)) {
            return chain;
        }
        return Arrays.stream(chain.split(CHAIN_SEPARATOR, -1))
                .map(segment -> resolve(segment, raceName))
                .collect(Collectors.joining(CHAIN_SEPARATOR));
    }
}
