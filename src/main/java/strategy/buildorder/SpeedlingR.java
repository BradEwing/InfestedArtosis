package strategy.buildorder;

import bwapi.Race;
import info.TechProgression;

/**
 * {@link Speedling} against a Random opponent, offered while its race is Unknown. When the race
 * resolves mid-game the build keeps playing; it is not replaced by the race variant. Expands only
 * on floating minerals, since the opponent may resolve to Zerg.
 */
public class SpeedlingR extends Speedling {

    public static final String NAME = "SpeedlingR";

    public SpeedlingR() {
        super(NAME);
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Random || race == Race.Unknown;
    }

    /**
     * False. The build has no tech unit to be larva bound on: every larva goes to a Zergling the
     * Spawning Pool already allows, and its own hatchery request at the mineral bar is the one producer.
     */
    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return false;
    }

    @Override
    protected boolean wantsBaseAdvantage() {
        return false;
    }
}
