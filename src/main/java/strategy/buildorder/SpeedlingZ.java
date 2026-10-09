package strategy.buildorder;

import bwapi.Race;
import info.TechProgression;

/**
 * {@link Speedling} against Zerg. Expands only on floating minerals: Zerg ling play does not chase
 * base parity.
 */
public class SpeedlingZ extends Speedling {

    public static final String NAME = "SpeedlingZ";

    public SpeedlingZ() {
        super(NAME);
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Zerg;
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
