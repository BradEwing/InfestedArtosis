package strategy.buildorder.protoss;

import info.TechProgression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchMutaTest {

    private static final int NO_MACRO_HATCHERY = 0;

    private static final int ONE_MACRO_HATCHERY = 1;

    private static TechProgression withPool() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);
        return techProgression;
    }

    private static TechProgression withPlannedDen() {
        TechProgression techProgression = withPool();
        techProgression.setPlannedDen(true);
        return techProgression;
    }

    private static void queueChamber(TechProgression techProgression) {
        techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() + 1);
    }

    @Test
    void aSporeRequiredWithNoChamberQueuesExactlyOneChamberInOnePass() {
        TechProgression techProgression = withPlannedDen();
        int chambers = 0;
        queueChamber(techProgression);
        chambers++;
        if (ThreeHatchMuta.wantEvolutionChamber(techProgression)) {
            queueChamber(techProgression);
            chambers++;
        }

        assertEquals(1, chambers);
        assertEquals(1, techProgression.evolutionChambers());
    }

    @Test
    void aSporeRequirementAloneDoesNotAskForAnUpgradeChamber() {
        assertFalse(ThreeHatchMuta.wantEvolutionChamber(withPool()));
    }

    @Test
    void takesItsCarapaceChamberOnceTheDenIsPlanned() {
        assertTrue(ThreeHatchMuta.wantEvolutionChamber(withPlannedDen()));
    }

    @Test
    void neverTakesASecondChamberForItsSingleUpgradeLine() {
        TechProgression techProgression = withPlannedDen();
        techProgression.setHydraliskDen(true);
        techProgression.setEvolutionChambers(1);

        assertFalse(ThreeHatchMuta.wantEvolutionChamber(techProgression));
    }

    /**
     * Game LU01I000: the shared larva-bound step bought macro hatchery #1, so the build's own flag
     * never flipped and the Den was never planned.
     */
    @Test
    void plansTheDenOnceTheSharedStepBoughtTheMacroHatchery() {
        assertTrue(ThreeHatchMuta.shouldPlanHydraliskDen(withPool(), false, ONE_MACRO_HATCHERY));
    }

    @Test
    void withholdsTheDenWithNoMacroHatcheryAndTheFlagUnset() {
        assertFalse(ThreeHatchMuta.shouldPlanHydraliskDen(withPool(), false, NO_MACRO_HATCHERY));
    }

    @Test
    void plansTheDenOnceTheBuildPlannedItsOwnMacroHatchery() {
        assertTrue(ThreeHatchMuta.shouldPlanHydraliskDen(withPool(), true, NO_MACRO_HATCHERY));
    }

    @Test
    void withholdsTheDenWithoutASpawningPool() {
        assertFalse(ThreeHatchMuta.shouldPlanHydraliskDen(new TechProgression(), false, ONE_MACRO_HATCHERY));
    }

    @Test
    void withholdsTheDenWhileOneIsAlreadyPlanned() {
        TechProgression techProgression = withPool();
        techProgression.setPlannedDen(true);

        assertFalse(ThreeHatchMuta.shouldPlanHydraliskDen(techProgression, false, ONE_MACRO_HATCHERY));
    }

    @Test
    void withholdsTheDenOnceItStands() {
        TechProgression techProgression = withPool();
        techProgression.setHydraliskDen(true);

        assertFalse(ThreeHatchMuta.shouldPlanHydraliskDen(techProgression, true, ONE_MACRO_HATCHERY));
    }
}
