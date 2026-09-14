package strategy.buildorder.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import info.TechProgression;
import macro.AdvancedUnitEligibility;
import macro.plan.Plan;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SpireMacroHatchery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoHatchMutaTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final int NO_SPIRE = 0;

    private static final int ONE_SPIRE = 1;

    private static final int NO_LARVA = 0;

    private static final int ONE_HATCHERY = 1;

    private static final int TWO_HATCHERIES = 2;

    private static final int NO_MACRO_HATCHERY = 0;

    private static final int ONE_MACRO_HATCHERY = 1;

    private static final int LARVA_STARVED_FRAME = 9917;

    private static final TilePosition MAIN_TILE = new TilePosition(117, 119);

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

    @Test
    void derivesTheMutaliskWithASpireAndTheGathererFloor() {
        assertTrue(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 0, 9, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskBelowTheGathererFloor() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 0, 9, GATHERER_FLOOR - 1));
    }

    @Test
    void withholdsTheMutaliskWithoutASpire() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(new TechProgression(), 0, 9, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskOnceTheCountIsMet() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 9, 9, GATHERER_FLOOR));
    }

    @Test
    void derivesTheOverlordWhileSupplyIsTight() {
        assertTrue(TwoHatchMuta.shouldPlanOverlord(2, 3, false));
    }

    @Test
    void withholdsTheOverlordWhileSupplyIsExcess() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(2, 3, true));
    }

    @Test
    void withholdsTheOverlordOnceTheCountIsMet() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(2, 4, false));
    }

    @Test
    void withholdsTheOverlordBelowTwoSpires() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(1, 3, false));
    }

    @Test
    void requestsAMacroHatcheryWhileLarvaBoundWithASpireAtTheFloatBars() {
        assertTrue(TwoHatchMuta.shouldPlanMacroHatchery(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES,
                SpireMacroHatchery.FLOAT_MINERALS, SpireMacroHatchery.FLOAT_GAS, NO_MACRO_HATCHERY));
    }

    /**
     * Game LBIDH0GO at frame 9917: one hatchery left after the natural Lair fell, no larva, and
     * 397 minerals and 214 gas, taken here as unreserved, with the Spire committed.
     */
    @Test
    void requestsAMacroHatcheryOnTheLastHatcheryWithTheNaturalLost() {
        assertTrue(TwoHatchMuta.shouldPlanMacroHatchery(ONE_SPIRE, NO_LARVA, ONE_HATCHERY, 397, 214,
                NO_MACRO_HATCHERY));
    }

    @Test
    void theRequestedPlanIsAMacroHatcheryOnTheMainTile() {
        Plan plan = BuildOrder.macroHatcheryPlan(LARVA_STARVED_FRAME, MAIN_TILE);

        assertEquals(UnitType.Zerg_Hatchery, plan.getPlannedUnit());
        assertTrue(plan.isMacroHatchery());
        assertEquals(MAIN_TILE, plan.getBuildPosition());
        assertEquals(LARVA_STARVED_FRAME, plan.getPriority());
    }

    @Test
    void doesNotRequestAMacroHatcheryBeforeASpireIsCommitted() {
        assertFalse(TwoHatchMuta.shouldPlanMacroHatchery(NO_SPIRE, NO_LARVA, TWO_HATCHERIES,
                SpireMacroHatchery.FLOAT_MINERALS, SpireMacroHatchery.FLOAT_GAS, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileOneIsQueuedOrMorphing() {
        assertFalse(TwoHatchMuta.shouldPlanMacroHatchery(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES,
                SpireMacroHatchery.FLOAT_MINERALS, SpireMacroHatchery.FLOAT_GAS, ONE_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileLarvaIsNotShort() {
        assertFalse(TwoHatchMuta.shouldPlanMacroHatchery(ONE_SPIRE, TWO_HATCHERIES, TWO_HATCHERIES,
                SpireMacroHatchery.FLOAT_MINERALS, SpireMacroHatchery.FLOAT_GAS, NO_MACRO_HATCHERY));
    }
}
