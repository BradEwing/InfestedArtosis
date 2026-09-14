package strategy.buildorder;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpireMacroHatcheryTest {

    private static final int NO_SPIRE = 0;

    private static final int ONE_SPIRE = 1;

    private static final int NO_LARVA = 0;

    private static final int TWO_HATCHERIES = 2;

    /**
     * Game L9NW30JG from 10:36: two hatcheries, at most one larva, and a peak bank of 638 minerals
     * and 530 gas, taken here as unreserved. The 1050 mineral bar the build used to wait for was
     * never reached.
     */
    @Test
    void requestsAHatcheryWhileLarvaStarvedAndFloatingBothResources() {
        assertTrue(SpireMacroHatchery.shouldPlan(ONE_SPIRE, 1, TWO_HATCHERIES, 638, 530));
    }

    @Test
    void requestsAHatcheryAtTheFloatBars() {
        assertTrue(SpireMacroHatchery.shouldPlan(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES,
                SpireMacroHatchery.FLOAT_MINERALS, SpireMacroHatchery.FLOAT_GAS));
    }

    @Test
    void theMineralBarCoversTheHatcheryItBuys() {
        assertEquals(UnitType.Zerg_Hatchery.mineralPrice(), SpireMacroHatchery.FLOAT_MINERALS);
    }

    @Test
    void doesNotRequestAHatcheryWhileLarvaIsNotShort() {
        assertFalse(SpireMacroHatchery.shouldPlan(ONE_SPIRE, TWO_HATCHERIES, TWO_HATCHERIES, 638, 530));
    }

    @Test
    void doesNotRequestAHatcheryOnFloatingMineralsAlone() {
        assertFalse(SpireMacroHatchery.shouldPlan(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES, 638,
                SpireMacroHatchery.FLOAT_GAS - 1));
    }

    @Test
    void doesNotRequestAHatcheryOnFloatingGasAlone() {
        assertFalse(SpireMacroHatchery.shouldPlan(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES,
                SpireMacroHatchery.FLOAT_MINERALS - 1, 530));
    }

    /**
     * The same 638 minerals and 530 gas with a Mutalisk pair and an upgrade already claiming most
     * of it. The banks are read after reservations, which a plan takes when it is scheduled, so a
     * committed bank is not a float.
     */
    @Test
    void doesNotRequestAHatcheryWhileTheBankIsReservedByScheduledPlans() {
        int reservedMinerals = 2 * UnitType.Zerg_Mutalisk.mineralPrice() + 150;
        int reservedGas = 2 * UnitType.Zerg_Mutalisk.gasPrice() + 150;

        assertFalse(SpireMacroHatchery.shouldPlan(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES,
                638 - reservedMinerals, 530 - reservedGas));
    }

    @Test
    void doesNotRequestAHatcheryWhileBankingForTheSpire() {
        assertFalse(SpireMacroHatchery.shouldPlan(NO_SPIRE, NO_LARVA, TWO_HATCHERIES, 638, 530));
    }
}
