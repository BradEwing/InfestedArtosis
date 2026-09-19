package strategy.buildorder;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import strategy.buildorder.LarvaBoundMacroHatchery.Gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LarvaBoundMacroHatcheryTest {

    private static final boolean TECH_NOT_READY = false;

    private static final boolean TECH_READY = true;

    private static final int NO_LARVA = 0;

    private static final int TWO_HATCHERIES = 2;

    private static final int NO_ENEMIES = 0;

    private static final int NO_MACRO_HATCHERY = 0;

    /**
     * Game L9NW30JG from 10:36: two hatcheries, at most one larva, and a peak bank of 638 minerals
     * and 530 gas, taken here as unreserved. The 1050 mineral bar the build used to wait for was
     * never reached.
     */
    @Test
    void requestsAHatcheryWhileLarvaStarvedAndFloatingBothResources() {
        assertTrue(LarvaBoundMacroHatchery.shouldPlan(TECH_READY, 1, TWO_HATCHERIES, 638, 530,
                NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void requestsAHatcheryAtTheFloatBars() {
        assertTrue(LarvaBoundMacroHatchery.shouldPlan(TECH_READY, NO_LARVA, TWO_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS,
                NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void theMineralBarCoversTheHatcheryItBuys() {
        assertEquals(UnitType.Zerg_Hatchery.mineralPrice(), LarvaBoundMacroHatchery.FLOAT_MINERALS);
    }

    @Test
    void doesNotRequestAHatcheryWhileLarvaIsNotShort() {
        assertEquals(Gate.LARVA_NOT_SHORT, LarvaBoundMacroHatchery.evaluate(TECH_READY, TWO_HATCHERIES,
                TWO_HATCHERIES, 638, 530, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAHatcheryOnFloatingMineralsAlone() {
        assertEquals(Gate.NOT_FLOATING, LarvaBoundMacroHatchery.evaluate(TECH_READY, NO_LARVA, TWO_HATCHERIES,
                638, LarvaBoundMacroHatchery.FLOAT_GAS - 1, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAHatcheryOnFloatingGasAlone() {
        assertEquals(Gate.NOT_FLOATING, LarvaBoundMacroHatchery.evaluate(TECH_READY, NO_LARVA, TWO_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS - 1, 530, NO_ENEMIES, NO_MACRO_HATCHERY));
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

        assertFalse(LarvaBoundMacroHatchery.shouldPlan(TECH_READY, NO_LARVA, TWO_HATCHERIES,
                638 - reservedMinerals, 530 - reservedGas, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAHatcheryWhileBankingForTheTech() {
        assertEquals(Gate.TECH_NOT_READY, LarvaBoundMacroHatchery.evaluate(TECH_NOT_READY, NO_LARVA,
                TWO_HATCHERIES, 638, 530, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    /**
     * Game LC0QF0AG plan 140: queued on the frame the previous macro hatchery finished, with six
     * enemy ground units known at our bases, and lost to them.
     */
    @Test
    void doesNotRequestAHatcheryWhileEnemiesAreKnownAtOurBases() {
        assertEquals(Gate.THREAT, LarvaBoundMacroHatchery.evaluate(TECH_READY, NO_LARVA, TWO_HATCHERIES,
                638, 530, 6, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAHatcheryWhileOneIsOutstanding() {
        assertEquals(Gate.OUTSTANDING, LarvaBoundMacroHatchery.evaluate(TECH_READY, NO_LARVA, TWO_HATCHERIES,
                638, 530, NO_ENEMIES, 1));
    }

    @Test
    void onlyALarvaBoundFloatIsARequest() {
        assertFalse(Gate.LARVA_NOT_SHORT.isRequest());
        assertFalse(Gate.NOT_FLOATING.isRequest());
        assertTrue(Gate.TECH_NOT_READY.isRequest());
        assertTrue(Gate.THREAT.isRequest());
        assertTrue(Gate.OUTSTANDING.isRequest());
        assertTrue(Gate.TRIGGER.isRequest());
    }
}
