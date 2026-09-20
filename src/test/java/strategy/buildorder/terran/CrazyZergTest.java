package strategy.buildorder.terran;

import info.TechProgression;
import macro.AdvancedUnitEligibility;
import org.junit.jupiter.api.Test;
import strategy.buildorder.LarvaBoundMacroHatchery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrazyZergTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final int NO_LARVA = 0;

    private static final int THREE_HATCHERIES = 3;

    private static final int NO_ENEMIES = 0;

    private static final int NO_MACRO_HATCHERY = 0;

    private static final int ONE_MACRO_HATCHERY = 1;

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

    @Test
    void derivesTheMutaliskWithASpireAndTheGathererFloor() {
        assertTrue(CrazyZerg.shouldPlanMutalisk(withSpire(), true, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskWithNoGatherers() {
        assertFalse(CrazyZerg.shouldPlanMutalisk(withSpire(), true, 0));
    }

    @Test
    void withholdsTheMutaliskBelowTheGathererFloor() {
        assertFalse(CrazyZerg.shouldPlanMutalisk(withSpire(), true, GATHERER_FLOOR - 1));
    }

    @Test
    void withholdsTheMutaliskWithoutASpire() {
        assertFalse(CrazyZerg.shouldPlanMutalisk(new TechProgression(), true, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskOnceTheCapIsReached() {
        assertFalse(CrazyZerg.shouldPlanMutalisk(withSpire(), false, GATHERER_FLOOR));
    }

    @Test
    void derivesTheOverlordWhileSupplyIsTight() {
        assertTrue(CrazyZerg.shouldPlanOverlord(1, 3, false));
    }

    @Test
    void withholdsTheOverlordWhileSupplyIsExcess() {
        assertFalse(CrazyZerg.shouldPlanOverlord(1, 3, true));
    }

    @Test
    void withholdsTheOverlordOnceTheCountIsMet() {
        assertFalse(CrazyZerg.shouldPlanOverlord(1, 4, false));
    }

    @Test
    void withholdsTheOverlordWithoutASpire() {
        assertFalse(CrazyZerg.shouldPlanOverlord(0, 3, false));
    }

    @Test
    void requestsAMacroHatcheryWhileLarvaBoundWithASpireAtTheFloatBars() {
        assertTrue(requestsMacroHatchery(withSpire(), NO_LARVA, THREE_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES,
                NO_MACRO_HATCHERY));
    }

    /**
     * Game LMR9R0MB at frame 14190, the first of 90 logged frames the gate would have triggered on:
     * three hatcheries, no larva, 419 minerals and 220 gas unreserved, with the Spire finished. The
     * build reached that state and bought nothing until frame 16456.
     */
    @Test
    void requestsAMacroHatcheryAtTheFrameTheStarvationRunBegan() {
        assertTrue(requestsMacroHatchery(withSpire(), NO_LARVA, THREE_HATCHERIES, 419, 220, NO_ENEMIES,
                NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryBeforeASpireIsFinished() {
        assertFalse(requestsMacroHatchery(new TechProgression(), NO_LARVA, THREE_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES,
                NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileTheCommittedSpireIsStillMorphing() {
        TechProgression spireMorphing = new TechProgression();
        spireMorphing.setPlannedSpire(true);

        assertFalse(requestsMacroHatchery(spireMorphing, NO_LARVA, THREE_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES,
                NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileEnemiesAreKnownAtOurBases() {
        assertFalse(requestsMacroHatchery(withSpire(), NO_LARVA, THREE_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, 1, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileOneIsQueuedOrMorphing() {
        assertFalse(requestsMacroHatchery(withSpire(), NO_LARVA, THREE_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES,
                ONE_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileLarvaIsNotShort() {
        assertFalse(requestsMacroHatchery(withSpire(), THREE_HATCHERIES, THREE_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES,
                NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryBelowTheFloatBars() {
        assertFalse(requestsMacroHatchery(withSpire(), NO_LARVA, THREE_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS - 1, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES,
                NO_MACRO_HATCHERY));
        assertFalse(requestsMacroHatchery(withSpire(), NO_LARVA, THREE_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS - 1, NO_ENEMIES,
                NO_MACRO_HATCHERY));
    }

    /**
     * The build reads the gate through its own tech condition rather than through a call it could
     * have left out. Asserted on the hook the template hands {@link LarvaBoundMacroHatchery#evaluate}.
     */
    private static boolean requestsMacroHatchery(TechProgression techProgression, int larva, int hatcheries,
                                                 int availableMinerals, int availableGas, int enemiesAtBases,
                                                 int outstandingMacroHatcheries) {
        return LarvaBoundMacroHatchery.shouldPlan(new CrazyZerg().macroHatcheryTechReady(techProgression), larva,
                hatcheries, availableMinerals, availableGas, enemiesAtBases, outstandingMacroHatcheries);
    }
}
