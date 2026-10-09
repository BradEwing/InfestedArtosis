package strategy.buildorder;

import bwapi.Race;
import info.TechProgression;

/**
 * {@link Speedling} against Protoss. Keeps a base advantage: a further base is requested whenever
 * the enemy holds as many resource depots as we do, as well as on floating minerals.
 */
public class SpeedlingP extends Speedling {

    public static final String NAME = "SpeedlingP";

    public SpeedlingP() {
        super(NAME);
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Protoss;
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
        return true;
    }
}
