package strategy.buildorder.terran;

import info.TechProgression;

/**
 * {@link LurkerDefilerUltra} with the capped {@link GuardianBranch} added: once the Hive stands on
 * three bases and three mined geysers it plans a Spire, Mutalisks, a Greater Spire and up to
 * {@value GuardianBranch#WAVE_CAP} Guardians, and keeps Hydralisks beside them as ground support.
 *
 * <p>A second terminal candidate at the handover from 2HatchMuta and 3HatchLurker, so the learning
 * module records its results apart from {@link LurkerDefilerUltra} and selects it by them.
 * IA_GUARDIAN_BRANCH=true offers only this build and =false only the other, see
 * {@link LurkerDefilerUltraTransition#candidates}.
 */
public class LurkerDefilerGuardian extends LurkerDefilerUltra {

    public static final String NAME = "LurkerDefilerGuardian";

    public LurkerDefilerGuardian() {
        super(NAME, true);
    }

    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return super.macroHatcheryTechReady(techProgression);
    }
}
