package strategy.buildorder.terran;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.AdvancedUnitEligibility;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.LarvaBoundMacroHatchery;
import strategy.buildorder.GasBoundHiveTech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrazyZergTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final int NO_LARVA = 0;

    private static final int THREE_HATCHERIES = 3;

    private static final int NO_ENEMIES = 0;

    private static final int NO_MACRO_HATCHERY = 0;

    private static final int ONE_MACRO_HATCHERY = 1;
    private static final boolean TECH_AVAILABLE = true;

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

    /**
     * Game LMR9R0MB from frame 14823 on: the Lair finished at 7623, two Extractors were ever
     * taken and one was alive, and the unreserved bank sat at or above 500 gas for the remaining
     * 5,236 frames. The build asked for no Queen's Nest in any of them.
     */
    @Test
    void asksForTheQueensNestOnATwoGeyserBankThatHeldTheBar() {
        assertEquals(GasBoundHiveTech.Gate.TRIGGER,
                GasBoundHiveTech.evaluate(TECH_AVAILABLE, 500, GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    @Test
    void asksForTheQueensNestAtTheBar() {
        assertTrue(GasBoundHiveTech.shouldPlan(TECH_AVAILABLE, GasBoundHiveTech.BRANCH_GAS,
                GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    @Test
    void withholdsTheQueensNestOnABankBelowTheBar() {
        assertEquals(GasBoundHiveTech.Gate.GAS_SHORT,
                GasBoundHiveTech.evaluate(TECH_AVAILABLE, GasBoundHiveTech.BRANCH_GAS - 1,
                        GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    @Test
    void withholdsTheQueensNestOnABankThatOnlyTouchedTheBar() {
        assertEquals(GasBoundHiveTech.Gate.GAS_SHORT,
                GasBoundHiveTech.evaluate(TECH_AVAILABLE, 1677, GasBoundHiveTech.SUSTAINED_FRAMES - 1));
    }

    @Test
    void withholdsTheQueensNestWhileTheLairIsUnfinished() {
        assertEquals(GasBoundHiveTech.Gate.TECH_UNAVAILABLE,
                GasBoundHiveTech.evaluate(false, 1677, GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    @Test
    void theHoldStartsAtTheFrameTheBankReachesTheBar() {
        assertEquals(7623, GasBoundHiveTech.holdSince(GasBoundHiveTech.NOT_HELD, 7622, 7623,
                GasBoundHiveTech.BRANCH_GAS));
    }

    @Test
    void theHoldKeepsItsStartWhileTheBankStaysAtTheBar() {
        assertEquals(7623, GasBoundHiveTech.holdSince(7623, 8000, 8001, 1677));
    }

    @Test
    void theHoldRestartsOnABankBelowTheBar() {
        assertEquals(GasBoundHiveTech.NOT_HELD,
                GasBoundHiveTech.holdSince(7623, 8000, 8001, GasBoundHiveTech.BRANCH_GAS - 1));
    }

    /**
     * The bank is sampled where the build order evaluates, so frames nothing looked at are not
     * frames the bar was held.
     */
    @Test
    void theHoldRestartsAfterAGapLongerThanTheWindow() {
        int gapped = 8000 + GasBoundHiveTech.SUSTAINED_FRAMES + 1;
        assertEquals(GasBoundHiveTech.NOT_HELD, GasBoundHiveTech.holdSince(7623, 8000, gapped, 1677));
    }

    @Test
    void readingTheHoldTwiceInOneFrameNeitherAdvancesNorRestartsIt() {
        int first = GasBoundHiveTech.holdSince(GasBoundHiveTech.NOT_HELD, 7622, 7623, 1677);
        assertEquals(first, GasBoundHiveTech.holdSince(first, 7623, 7623, 1677));
        assertEquals(0, GasBoundHiveTech.framesHeld(first, 7623));
    }

    @Test
    void framesHeldIsZeroWhileTheBarIsNotHeld() {
        assertEquals(0, GasBoundHiveTech.framesHeld(GasBoundHiveTech.NOT_HELD, 20059));
        assertEquals(480, GasBoundHiveTech.framesHeld(7623, 8103));
    }

    /**
     * An unavailable structure is not a withheld one, so the gate writes no telemetry row for it.
     */
    @Test
    void theUnavailableGateIsNotARequest() {
        assertFalse(GasBoundHiveTech.Gate.TECH_UNAVAILABLE.isRequest());
        assertTrue(GasBoundHiveTech.Gate.GAS_SHORT.isRequest());
        assertTrue(GasBoundHiveTech.Gate.TRIGGER.isRequest());
    }

    private static int upgradePriority(UpgradeType upgradeType, UnitType unitType, int living) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < living; i++) {
            count.addUnit(unitType);
        }
        return new CrazyZerg().upgradePriority(upgradeType, count, 15000);
    }

    @Test
    void flyerAttacksPollsAheadOfMutalisksAtTheMutaliskCap() {
        assertEquals(15000, upgradePriority(UpgradeType.Zerg_Flyer_Attacks, UnitType.Zerg_Mutalisk, 8));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Zerg_Flyer_Attacks, UnitType.Zerg_Mutalisk, 9));
    }

    @Test
    void ultraliskUpgradesPollAheadOfUltralisksOnceTheTriggerIsAlive() {
        int trigger = CrazyZerg.ULTRALISKS_BEFORE_ULTRALISK_UPGRADE_PRIORITY;

        assertEquals(15000, upgradePriority(UpgradeType.Chitinous_Plating, UnitType.Zerg_Ultralisk, trigger - 1));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Chitinous_Plating, UnitType.Zerg_Ultralisk, trigger));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Anabolic_Synthesis, UnitType.Zerg_Ultralisk, trigger + 1));
    }

    @Test
    void groundUpgradesKeepTheirFramePriority() {
        assertEquals(15000, upgradePriority(UpgradeType.Zerg_Carapace, UnitType.Zerg_Ultralisk, 20));
        assertEquals(15000, upgradePriority(UpgradeType.Zerg_Melee_Attacks, UnitType.Zerg_Zergling, 40));
    }
}
