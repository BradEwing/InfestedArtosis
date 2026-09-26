package strategy.buildorder.terran;

import bwapi.TechType;
import bwapi.UnitType;
import info.TechProgression;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LurkerDefilerUltraTest {

    private static final Time EARLY = new Time(8, 0);

    private static final int THREE_BASES = 3;

    private static final int TWO_BASES = 2;

    private static final boolean NO_LAIR_WANTED = false;

    private static final boolean ULTRALISKS_ALLOWED = true;

    private static final boolean ULTRALISKS_BARRED = false;

    /** The tech a 2HatchMuta hands over with: Spawning Pool, Lair and Spire, nothing else. */
    private static TechProgression twoHatchMutaTech() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);
        techProgression.setLair(true);
        techProgression.setSpire(true);
        return techProgression;
    }

    private static LurkerDefilerUltra.TechStep next(TechProgression techProgression, boolean ultraliskGate) {
        return LurkerDefilerUltra.nextTechStep(techProgression, NO_LAIR_WANTED, THREE_BASES, EARLY, ultraliskGate);
    }

    @Test
    void walksTheTechPathInOrderFromATwoHatchMutaHandover() {
        TechProgression techProgression = twoHatchMutaTech();

        assertEquals(LurkerDefilerUltra.TechStep.HYDRALISK_DEN, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedDen(true);

        assertEquals(LurkerDefilerUltra.TechStep.EVOLUTION_CHAMBER, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedEvolutionChambers(1);

        assertEquals(LurkerDefilerUltra.TechStep.QUEENS_NEST, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedQueensNest(true);

        assertEquals(LurkerDefilerUltra.TechStep.NONE, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedDen(false);
        techProgression.setHydraliskDen(true);

        assertEquals(LurkerDefilerUltra.TechStep.LURKER_ASPECT, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedLurker(true);
        techProgression.setPlannedEvolutionChambers(0);
        techProgression.setEvolutionChambers(1);

        assertEquals(LurkerDefilerUltra.TechStep.NONE, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedQueensNest(false);
        techProgression.setQueensNest(true);

        assertEquals(LurkerDefilerUltra.TechStep.HIVE, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedHive(true);

        assertEquals(LurkerDefilerUltra.TechStep.NONE, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedHive(false);
        techProgression.setHive(true);

        assertEquals(LurkerDefilerUltra.TechStep.DEFILER_MOUND, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedDefilerMound(true);

        assertEquals(LurkerDefilerUltra.TechStep.EVOLUTION_CHAMBER, next(techProgression, ULTRALISKS_BARRED));
        techProgression.setPlannedEvolutionChambers(1);

        assertEquals(LurkerDefilerUltra.TechStep.NONE, next(techProgression, ULTRALISKS_BARRED));
        assertEquals(LurkerDefilerUltra.TechStep.ULTRALISK_CAVERN, next(techProgression, ULTRALISKS_ALLOWED));
    }

    @Test
    void theDefilerMoundComesTheFrameTheHiveFinishesAheadOfEveryOtherOpenStep() {
        TechProgression techProgression = twoHatchMutaTech();
        techProgression.setQueensNest(true);
        techProgression.setHive(true);

        assertTrue(techProgression.canPlanHydraliskDen());
        assertTrue(techProgression.canPlanUltraliskCavern());
        assertEquals(LurkerDefilerUltra.TechStep.DEFILER_MOUND, LurkerDefilerUltra.nextTechStep(techProgression,
                true, THREE_BASES, EARLY, ULTRALISKS_ALLOWED));
    }

    @Test
    void theHiveComesTheFrameTheQueensNestFinishesAheadOfTheDenAndChamber() {
        TechProgression techProgression = twoHatchMutaTech();
        techProgression.setQueensNest(true);

        assertTrue(techProgression.canPlanHydraliskDen());
        assertEquals(LurkerDefilerUltra.TechStep.HIVE, next(techProgression, ULTRALISKS_BARRED));
    }

    @Test
    void aMissingLairIsPlannedBeforeTheDen() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);

        assertEquals(LurkerDefilerUltra.TechStep.LAIR, LurkerDefilerUltra.nextTechStep(techProgression, true,
                THREE_BASES, EARLY, ULTRALISKS_BARRED));
    }

    @Test
    void stepsAlreadyStandingAreSkippedForAThreeHatchLurkerHandover() {
        TechProgression techProgression = twoHatchMutaTech();
        techProgression.setHydraliskDen(true);
        techProgression.setLurker(true);
        techProgression.setEvolutionChambers(2);

        assertEquals(LurkerDefilerUltra.TechStep.QUEENS_NEST, next(techProgression, ULTRALISKS_BARRED));
    }

    @Test
    void theQueensNestWaitsForThreeBasesOrItsDueTime() {
        assertFalse(LurkerDefilerUltra.queensNestDue(TWO_BASES, EARLY));
        assertTrue(LurkerDefilerUltra.queensNestDue(THREE_BASES, EARLY));
        assertTrue(LurkerDefilerUltra.queensNestDue(TWO_BASES, LurkerDefilerUltra.QUEENS_NEST_DUE));
        assertFalse(LurkerDefilerUltra.queensNestDue(TWO_BASES,
                new Time(LurkerDefilerUltra.QUEENS_NEST_DUE.getFrames() - 1)));
    }

    @Test
    void theQueensNestOnTwoBasesIsHeldUntilItsDueTime() {
        TechProgression techProgression = twoHatchMutaTech();
        techProgression.setHydraliskDen(true);
        techProgression.setLurker(true);
        techProgression.setEvolutionChambers(1);

        assertEquals(LurkerDefilerUltra.TechStep.NONE, LurkerDefilerUltra.nextTechStep(techProgression,
                NO_LAIR_WANTED, TWO_BASES, EARLY, ULTRALISKS_BARRED));
        assertEquals(LurkerDefilerUltra.TechStep.QUEENS_NEST, LurkerDefilerUltra.nextTechStep(techProgression,
                NO_LAIR_WANTED, TWO_BASES, LurkerDefilerUltra.QUEENS_NEST_DUE, ULTRALISKS_BARRED));
    }

    @Test
    void theQueensNestFallbackLeavesTimeForTheNestAndHiveBeforeTheHiveIsDue() {
        int buildFrames = UnitType.Zerg_Queens_Nest.buildTime() + UnitType.Zerg_Hive.buildTime();

        assertEquals(LurkerDefilerUltra.HIVE_DUE.getFrames() - buildFrames,
                LurkerDefilerUltra.QUEENS_NEST_DUE.getFrames());
        assertTrue(LurkerDefilerUltra.QUEENS_NEST_DUE.getFrames() > 0);
    }

    @Test
    void consumeIsResearchedBeforePlague() {
        TechProgression techProgression = new TechProgression();
        assertEquals(TechType.None, LurkerDefilerUltra.nextDefilerResearch(techProgression));

        techProgression.setDefilerMound(true);
        assertEquals(TechType.Consume, LurkerDefilerUltra.nextDefilerResearch(techProgression));

        techProgression.setPlannedConsume(true);
        assertEquals(TechType.None, LurkerDefilerUltra.nextDefilerResearch(techProgression));

        techProgression.setConsume(true);
        assertEquals(TechType.Plague, LurkerDefilerUltra.nextDefilerResearch(techProgression));

        techProgression.setPlannedPlague(true);
        assertEquals(TechType.None, LurkerDefilerUltra.nextDefilerResearch(techProgression));
    }

    @Test
    void ultralisksWaitForADefilerAndFourMiningGeysers() {
        assertFalse(LurkerDefilerUltra.ultraliskGate(0, 6));
        assertFalse(LurkerDefilerUltra.ultraliskGate(1, 3));
        assertTrue(LurkerDefilerUltra.ultraliskGate(1, 4));
        assertTrue(LurkerDefilerUltra.ultraliskGate(3, 5));
    }

    @Test
    void theUltraliskCavernIsBarredUntilTheGasGateOpens() {
        TechProgression techProgression = twoHatchMutaTech();
        techProgression.setHydraliskDen(true);
        techProgression.setLurker(true);
        techProgression.setEvolutionChambers(2);
        techProgression.setQueensNest(true);
        techProgression.setHive(true);
        techProgression.setDefilerMound(true);

        assertEquals(LurkerDefilerUltra.TechStep.NONE,
                next(techProgression, LurkerDefilerUltra.ultraliskGate(1, 3)));
        assertEquals(LurkerDefilerUltra.TechStep.ULTRALISK_CAVERN,
                next(techProgression, LurkerDefilerUltra.ultraliskGate(1, 4)));
    }

    @Test
    void macroHatcheriesAreCappedAtTwo() {
        assertTrue(LurkerDefilerUltra.macroHatcheryAllowed(4, 0));
        assertTrue(LurkerDefilerUltra.macroHatcheryAllowed(4, 1));
        assertFalse(LurkerDefilerUltra.macroHatcheryAllowed(4, 2));
        assertFalse(LurkerDefilerUltra.macroHatcheryAllowed(6, 3));
    }

    @Test
    void macroHatcheriesWaitForFourBases() {
        assertFalse(LurkerDefilerUltra.macroHatcheryAllowed(3, 0));
        assertTrue(LurkerDefilerUltra.macroHatcheryAllowed(5, 0));
    }

    @Test
    void defilersGoAheadOfTheAdvancedUnitBand() {
        assertTrue(LurkerDefilerUltra.DEFILER_PRIORITY < UnitPlan.ADVANCED_UNIT_PRIORITY);
        assertTrue(LurkerDefilerUltra.DEFILER_PRIORITY > UnitPlan.DRONE_ROUND_PRIORITY);
    }

    @Test
    void hydralisksFeedTheLurkerTargetAndAnswerFlyers() {
        assertEquals(0, LurkerDefilerUltra.hydraliskTarget(false, 0, 0));
        assertEquals(LurkerDefilerUltra.LURKER_TARGET, LurkerDefilerUltra.hydraliskTarget(true, 0, 0));
        assertEquals(2, LurkerDefilerUltra.hydraliskTarget(true, LurkerDefilerUltra.LURKER_TARGET + 1, 1));
        assertEquals(LurkerDefilerUltra.MAX_ANTI_AIR_HYDRALISKS, LurkerDefilerUltra.hydraliskTarget(false, 0, 40));
    }

    @Test
    void zerglingsRiseWithTheHiveAndKeepAHigherMatchupTarget() {
        assertEquals(LurkerDefilerUltra.LAIR_ZERGLINGS, LurkerDefilerUltra.zerglingTarget(0, true, false));
        assertEquals(LurkerDefilerUltra.HIVE_ZERGLINGS, LurkerDefilerUltra.zerglingTarget(0, true, true));
        assertEquals(30, LurkerDefilerUltra.zerglingTarget(30, true, true));
        assertEquals(TerranBase.MAX_ZERGLINGS, LurkerDefilerUltra.zerglingTarget(TerranBase.MAX_ZERGLINGS, true, true));
    }

    @Test
    void noZerglingsAreAskedForBeforeTheSpawningPoolFinishes() {
        assertEquals(0, LurkerDefilerUltra.zerglingTarget(0, false, true));
        assertEquals(0, LurkerDefilerUltra.zerglingTarget(20, false, false));
    }

    @Test
    void belowFourBasesTheBuildExpands() {
        assertEquals(LurkerDefilerUltra.HatcheryStep.NEW_BASE, LurkerDefilerUltra.hatcheryStep(3, false, false, 3, 0));
        assertEquals(LurkerDefilerUltra.HatcheryStep.NEW_BASE, LurkerDefilerUltra.hatcheryStep(3, false, true, 3, 0));
    }

    @Test
    void onFourBasesAFloatBuysAMacroHatcheryUpToTheCap() {
        assertEquals(LurkerDefilerUltra.HatcheryStep.NONE, LurkerDefilerUltra.hatcheryStep(4, false, false, 4, 0));
        assertEquals(LurkerDefilerUltra.HatcheryStep.MACRO_HATCHERY,
                LurkerDefilerUltra.hatcheryStep(4, false, true, 4, 0));
        assertEquals(LurkerDefilerUltra.HatcheryStep.MACRO_HATCHERY,
                LurkerDefilerUltra.hatcheryStep(4, false, true, 4, 1));
    }

    @Test
    void aFloatPastTheMacroCapTakesANewBase() {
        assertEquals(LurkerDefilerUltra.HatcheryStep.NEW_BASE, LurkerDefilerUltra.hatcheryStep(4, false, true, 4, 2));
    }

    @Test
    void baseParityExpandsAheadOfAMacroHatchery() {
        assertEquals(LurkerDefilerUltra.HatcheryStep.NEW_BASE, LurkerDefilerUltra.hatcheryStep(4, true, true, 4, 0));
    }

    @Test
    void aReservedFourthBaseCountsTowardsTheTargetButNotTowardsTheMacroFloor() {
        assertEquals(LurkerDefilerUltra.HatcheryStep.NEW_BASE, LurkerDefilerUltra.hatcheryStep(4, false, true, 3, 0));
    }

    @Test
    void wantsHiveTechAndIsLarvaBoundOnLurkerTech() {
        LurkerDefilerUltra build = new LurkerDefilerUltra();
        assertTrue(build.needLair());
        assertTrue(build.needHive());
        assertFalse(build.isOpener());

        TechProgression techProgression = new TechProgression();
        techProgression.setLair(true);
        assertFalse(build.macroHatcheryTechReady(techProgression));
        techProgression.setHydraliskDen(true);
        assertTrue(build.macroHatcheryTechReady(techProgression));
    }
}
