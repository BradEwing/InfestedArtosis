package strategy.buildorder;

import bwapi.UnitType;
import info.TechProgression;
import org.junit.jupiter.api.Test;
import strategy.buildorder.LarvaBoundMacroHatchery.Gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertTrue(Gate.PLACEMENT_UNAVAILABLE.isRequest());
        assertTrue(Gate.TRIGGER.isRequest());
    }

    /**
     * A request that opens every gate and still produces no plan is a withheld row, not a trigger.
     * The logger writes MACRO_HATCHERY_TRIGGER for the TRIGGER gate alone, so a value that must
     * report a lost request has to be a value of its own and must not be TRIGGER.
     */
    @Test
    void aLostPlacementIsAWithheldRequestRatherThanATrigger() {
        assertNotEquals(Gate.TRIGGER, Gate.PLACEMENT_UNAVAILABLE);
        assertTrue(Gate.PLACEMENT_UNAVAILABLE.isRequest());
    }

    /**
     * The gate the evaluation itself can reach is never PLACEMENT_UNAVAILABLE: the placement is
     * tried after every gate opens, so only the caller can reach it.
     */
    @Test
    void theEvaluationNeverReportsALostPlacement() {
        assertNotEquals(Gate.PLACEMENT_UNAVAILABLE, LarvaBoundMacroHatchery.evaluate(TECH_READY, NO_LARVA,
                TWO_HATCHERIES, 638, 530, NO_ENEMIES, NO_MACRO_HATCHERY));
        assertNotEquals(Gate.PLACEMENT_UNAVAILABLE, LarvaBoundMacroHatchery.evaluate(TECH_NOT_READY, NO_LARVA,
                TWO_HATCHERIES, 638, 530, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void theHydraliskConditionReadsAFinishedDen() {
        TechProgression den = new TechProgression();
        den.setHydraliskDen(true);

        assertTrue(LarvaBoundMacroHatchery.isHydraliskTechReady(den));
        assertFalse(LarvaBoundMacroHatchery.isHydraliskTechReady(new TechProgression()));
    }
}
