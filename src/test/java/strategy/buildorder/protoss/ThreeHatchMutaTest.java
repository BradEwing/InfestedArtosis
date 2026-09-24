package strategy.buildorder.protoss;

import info.TechProgression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchMutaTest {

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
}
