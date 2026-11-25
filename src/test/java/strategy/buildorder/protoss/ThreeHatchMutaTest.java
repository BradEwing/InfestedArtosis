package strategy.buildorder.protoss;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.GasBoundHiveTech;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchMutaTest {

    private static final int NO_MACRO_HATCHERY = 0;

    private static final int ONE_MACRO_HATCHERY = 1;

    private static final int DEN_HYDRALISKS = ThreeHatchMuta.HYDRALISKS_BEFORE_DEN_UPGRADES;

    private static final int MISSILE_HYDRALISKS = ThreeHatchMuta.HYDRALISKS_BEFORE_MISSILE_ATTACKS;

    private static final int MELEE_ZERGLINGS = ThreeHatchMuta.ZERGLINGS_BEFORE_MELEE_ATTACKS;

    private static final int FLYER_MUTALISKS = ThreeHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;

    private static final int SURPLUS_MINERALS = 400;

    private static final int FLOAT_GAS = ThreeHatchMuta.HYDRALISK_FLOAT_GAS;

    private static final int UPGRADE_QUEUED_FRAME = 14000;

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

    private static TechProgression midGame(int evolutionChambers) {
        TechProgression techProgression = withPool();
        techProgression.setLair(true);
        techProgression.setSpire(true);
        techProgression.setHydraliskDen(true);
        techProgression.setEvolutionChambers(evolutionChambers);
        return techProgression;
    }

    private static UnitTypeCount living(int hydralisks, int zerglings, int mutalisks) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < hydralisks; i++) {
            count.addUnit(UnitType.Zerg_Hydralisk);
        }
        for (int i = 0; i < zerglings; i++) {
            count.addUnit(UnitType.Zerg_Zergling);
        }
        for (int i = 0; i < mutalisks; i++) {
            count.addUnit(UnitType.Zerg_Mutalisk);
        }
        return count;
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
        if (ThreeHatchMuta.wantEvolutionChamber(techProgression, 0)) {
            queueChamber(techProgression);
            chambers++;
        }

        assertEquals(1, chambers);
        assertEquals(1, techProgression.evolutionChambers());
    }

    @Test
    void aSporeRequirementAloneDoesNotAskForAnUpgradeChamber() {
        assertFalse(ThreeHatchMuta.wantEvolutionChamber(withPool(), DEN_HYDRALISKS));
    }

    @Test
    void takesItsCarapaceChamberOnceTheDenIsPlanned() {
        assertTrue(ThreeHatchMuta.wantEvolutionChamber(withPlannedDen(), 0));
    }

    @Test
    void withholdsTheSecondChamberUntilTheFirstHydralisksAreAlive() {
        assertFalse(ThreeHatchMuta.wantEvolutionChamber(midGame(1), DEN_HYDRALISKS - 1));
    }

    @Test
    void takesTheSecondChamberOnceTheFirstStandsAndTheHydralisksAreAlive() {
        assertTrue(ThreeHatchMuta.wantEvolutionChamber(midGame(1), DEN_HYDRALISKS));
    }

    @Test
    void waitsOnAChamberInFlightBeforeTakingAnother() {
        TechProgression techProgression = midGame(1);
        queueChamber(techProgression);

        assertFalse(ThreeHatchMuta.wantEvolutionChamber(techProgression, DEN_HYDRALISKS));
    }

    @Test
    void neverTakesAThirdChamber() {
        assertFalse(ThreeHatchMuta.wantEvolutionChamber(midGame(2), MISSILE_HYDRALISKS));
    }

    @Test
    void withholdsTheHydraliskDenUpgradesBelowSixLivingHydralisks() {
        assertEquals(6, DEN_HYDRALISKS);
        assertFalse(ThreeHatchMuta.shouldPlanMuscularAugments(midGame(0), DEN_HYDRALISKS - 1));

        TechProgression withSpeed = midGame(0);
        withSpeed.setMuscularAugments(true);
        assertFalse(ThreeHatchMuta.shouldPlanGroovedSpines(withSpeed, DEN_HYDRALISKS - 1));
    }

    @Test
    void plansMuscularAugmentsAtSixLivingHydralisks() {
        assertTrue(ThreeHatchMuta.shouldPlanMuscularAugments(midGame(0), DEN_HYDRALISKS));
    }

    @Test
    void plansGroovedSpinesOnceMuscularAugmentsHasFreedTheDen() {
        TechProgression researching = midGame(0);
        researching.setPlannedMuscularAugments(true);
        assertFalse(ThreeHatchMuta.shouldPlanGroovedSpines(researching, DEN_HYDRALISKS));

        TechProgression researched = midGame(0);
        researched.setMuscularAugments(true);
        assertTrue(ThreeHatchMuta.shouldPlanGroovedSpines(researched, DEN_HYDRALISKS));
    }

    @Test
    void withholdsTheHydraliskDenUpgradesOnceQueuedOrResearched() {
        TechProgression queued = midGame(0);
        queued.setPlannedMuscularAugments(true);
        assertFalse(ThreeHatchMuta.shouldPlanMuscularAugments(queued, MISSILE_HYDRALISKS));

        TechProgression researched = midGame(0);
        researched.setMuscularAugments(true);
        researched.setGroovedSpines(true);
        assertFalse(ThreeHatchMuta.shouldPlanGroovedSpines(researched, MISSILE_HYDRALISKS));
    }

    @Test
    void withholdsMissileAttacksBelowTwelveLivingHydralisks() {
        assertEquals(12, MISSILE_HYDRALISKS);
        assertFalse(ThreeHatchMuta.shouldPlanMissileAttacks(midGame(2), MISSILE_HYDRALISKS - 1));
        assertFalse(ThreeHatchMuta.chamberUpgradesToPlan(midGame(2), MISSILE_HYDRALISKS - 1, 0)
                .contains(UpgradeType.Zerg_Missile_Attacks));
    }

    @Test
    void plansMissileAttacksAtTwelveLivingHydralisks() {
        assertTrue(ThreeHatchMuta.shouldPlanMissileAttacks(midGame(2), MISSILE_HYDRALISKS));
    }

    @Test
    void withholdsMissileAttacksWithoutAChamber() {
        assertFalse(ThreeHatchMuta.shouldPlanMissileAttacks(midGame(0), MISSILE_HYDRALISKS));
    }

    @Test
    void twoChambersResearchMissileAttacksAndCarapaceTogether() {
        assertEquals(Arrays.asList(UpgradeType.Zerg_Missile_Attacks, UpgradeType.Zerg_Carapace),
                ThreeHatchMuta.chamberUpgradesToPlan(midGame(2), MISSILE_HYDRALISKS, 0));
    }

    @Test
    void oneChamberResearchesOneLine() {
        assertEquals(Collections.singletonList(UpgradeType.Zerg_Missile_Attacks),
                ThreeHatchMuta.chamberUpgradesToPlan(midGame(1), MISSILE_HYDRALISKS, 0));
    }

    @Test
    void theSecondChamberTakesTheSecondLineWhileTheFirstResearches() {
        TechProgression techProgression = midGame(2);
        techProgression.setPlannedCarapaceUpgrades(true);

        assertEquals(Collections.singletonList(UpgradeType.Zerg_Missile_Attacks),
                ThreeHatchMuta.chamberUpgradesToPlan(techProgression, MISSILE_HYDRALISKS, 0));
    }

    @Test
    void noThirdLineIsQueuedWhileBothChambersAreClaimed() {
        TechProgression techProgression = midGame(2);
        techProgression.setCarapaceUpgrades(1);
        techProgression.setPlannedCarapaceUpgrades(true);
        techProgression.setPlannedRangedUpgrades(true);

        assertTrue(ThreeHatchMuta.chamberUpgradesToPlan(techProgression, MISSILE_HYDRALISKS, MELEE_ZERGLINGS).isEmpty());
    }

    @Test
    void carapaceAloneIsPlannedBeforeTheHydralisksAreOut() {
        assertEquals(Collections.singletonList(UpgradeType.Zerg_Carapace),
                ThreeHatchMuta.chamberUpgradesToPlan(midGame(1), 0, 0));
    }

    @Test
    void withholdsMeleeAttacksBeforeCarapaceOneCompletes() {
        TechProgression techProgression = midGame(2);
        techProgression.setPlannedCarapaceUpgrades(true);

        assertFalse(ThreeHatchMuta.shouldPlanMeleeAttacks(techProgression, MELEE_ZERGLINGS));
        assertFalse(ThreeHatchMuta.chamberUpgradesToPlan(techProgression, 0, MELEE_ZERGLINGS)
                .contains(UpgradeType.Zerg_Melee_Attacks));
    }

    @Test
    void withholdsMeleeAttacksBelowTheZerglingThreshold() {
        TechProgression techProgression = midGame(2);
        techProgression.setCarapaceUpgrades(1);

        assertFalse(ThreeHatchMuta.shouldPlanMeleeAttacks(techProgression, MELEE_ZERGLINGS - 1));
        assertFalse(ThreeHatchMuta.chamberUpgradesToPlan(techProgression, 0, MELEE_ZERGLINGS - 1)
                .contains(UpgradeType.Zerg_Melee_Attacks));
    }

    @Test
    void plansMeleeAttacksOnceCarapaceOneIsCompleteAndTheZerglingsAreAlive() {
        TechProgression techProgression = midGame(2);
        techProgression.setCarapaceUpgrades(1);

        assertTrue(ThreeHatchMuta.shouldPlanMeleeAttacks(techProgression, MELEE_ZERGLINGS));
    }

    @Test
    void meleeAttacksTakesTheFreeChamberAheadOfCarapaceTwo() {
        TechProgression techProgression = midGame(2);
        techProgression.setCarapaceUpgrades(1);
        techProgression.setPlannedRangedUpgrades(true);

        assertEquals(Collections.singletonList(UpgradeType.Zerg_Melee_Attacks),
                ThreeHatchMuta.chamberUpgradesToPlan(techProgression, MISSILE_HYDRALISKS, MELEE_ZERGLINGS));
    }

    @Test
    void carapaceThreeWaitsForTheHive() {
        TechProgression techProgression = midGame(2);
        techProgression.setCarapaceUpgrades(2);
        techProgression.setRangedUpgrades(2);
        assertTrue(ThreeHatchMuta.chamberUpgradesToPlan(techProgression, MISSILE_HYDRALISKS, 0).isEmpty());

        techProgression.setHive(true);
        assertTrue(ThreeHatchMuta.chamberUpgradesToPlan(techProgression, MISSILE_HYDRALISKS, 0)
                .contains(UpgradeType.Zerg_Carapace));
    }

    @Test
    void withholdsFlyerUpgradesBelowTheMutaliskThreshold() {
        assertNull(ThreeHatchMuta.flyerUpgradeToPlan(midGame(0), FLYER_MUTALISKS - 1));
    }

    @Test
    void withholdsFlyerUpgradesWithoutASpire() {
        TechProgression techProgression = midGame(0);
        techProgression.setSpire(false);

        assertNull(ThreeHatchMuta.flyerUpgradeToPlan(techProgression, FLYER_MUTALISKS));
    }

    @Test
    void plansFlyerAttacksWithASpireAndTheMutalisksAlive() {
        assertEquals(UpgradeType.Zerg_Flyer_Attacks, ThreeHatchMuta.flyerUpgradeToPlan(midGame(0), FLYER_MUTALISKS));
    }

    @Test
    void flyerCarapaceFollowsFlyerAttacks() {
        TechProgression techProgression = midGame(0);
        techProgression.setFlyerAttack(1);

        assertEquals(UpgradeType.Zerg_Flyer_Carapace, ThreeHatchMuta.flyerUpgradeToPlan(techProgression, FLYER_MUTALISKS));
    }

    @Test
    void theSpireResearchesOneFlyerLevelAtATime() {
        TechProgression attackInFlight = midGame(0);
        attackInFlight.setPlannedFlyerAttack(true);
        assertNull(ThreeHatchMuta.flyerUpgradeToPlan(attackInFlight, FLYER_MUTALISKS));

        TechProgression carapaceInFlight = midGame(0);
        carapaceInFlight.setFlyerAttack(1);
        carapaceInFlight.setPlannedFlyerDefense(true);
        assertNull(ThreeHatchMuta.flyerUpgradeToPlan(carapaceInFlight, FLYER_MUTALISKS));
    }

    @Test
    void flyerLevelThreeWaitsForTheHive() {
        TechProgression techProgression = midGame(0);
        techProgression.setFlyerAttack(2);
        techProgression.setFlyerDefense(2);
        assertNull(ThreeHatchMuta.flyerUpgradeToPlan(techProgression, FLYER_MUTALISKS));

        techProgression.setHive(true);
        assertEquals(UpgradeType.Zerg_Flyer_Attacks, ThreeHatchMuta.flyerUpgradeToPlan(techProgression, FLYER_MUTALISKS));
    }

    @Test
    void theBuildAsksForAHive() {
        assertTrue(new ThreeHatchMuta().needHive());
    }

    @Test
    void theHiveBranchWaitsForTheSpireTheDenAndTheHydralisks() {
        assertFalse(ThreeHatchMuta.hiveBranchReady(midGame(2), MISSILE_HYDRALISKS - 1));

        TechProgression noSpire = midGame(2);
        noSpire.setSpire(false);
        assertFalse(ThreeHatchMuta.hiveBranchReady(noSpire, MISSILE_HYDRALISKS));

        TechProgression noDen = midGame(2);
        noDen.setHydraliskDen(false);
        assertFalse(ThreeHatchMuta.hiveBranchReady(noDen, MISSILE_HYDRALISKS));

        assertTrue(ThreeHatchMuta.hiveBranchReady(midGame(2), MISSILE_HYDRALISKS));
    }

    @Test
    void theQueensNestIsRequestedOnceTheGasGateOpens() {
        TechProgression techProgression = midGame(2);
        boolean techAvailable = techProgression.canPlanQueensNest()
                && ThreeHatchMuta.hiveBranchReady(techProgression, MISSILE_HYDRALISKS);

        assertTrue(GasBoundHiveTech.shouldPlan(techAvailable, GasBoundHiveTech.BRANCH_GAS,
                GasBoundHiveTech.SUSTAINED_FRAMES));
        assertFalse(GasBoundHiveTech.shouldPlan(techAvailable, GasBoundHiveTech.BRANCH_GAS - 1,
                GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    @Test
    void everyGatedUpgradeIsQueuedAheadOfTheHydraliskStream() {
        TechProgression techProgression = midGame(2);
        techProgression.setHive(true);
        UnitTypeCount count = living(MISSILE_HYDRALISKS, MELEE_ZERGLINGS, FLYER_MUTALISKS);
        ThreeHatchMuta build = new ThreeHatchMuta();

        for (UpgradeType upgradeType : Arrays.asList(UpgradeType.Muscular_Augments, UpgradeType.Grooved_Spines,
                UpgradeType.Zerg_Missile_Attacks, UpgradeType.Zerg_Carapace, UpgradeType.Zerg_Melee_Attacks,
                UpgradeType.Zerg_Flyer_Attacks, UpgradeType.Zerg_Flyer_Carapace, UpgradeType.Adrenal_Glands)) {
            assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY,
                    build.upgradePriority(upgradeType, count, techProgression, UPGRADE_QUEUED_FRAME), upgradeType.toString());
        }
        assertTrue(BuildOrder.ARMY_UPGRADE_PRIORITY < UnitPlan.ADVANCED_UNIT_PRIORITY);
    }

    @Test
    void eachUpgradeMovesAheadAtTheCountThatPlansIt() {
        ThreeHatchMuta build = new ThreeHatchMuta();

        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, build.upgradePriority(UpgradeType.Muscular_Augments,
                living(DEN_HYDRALISKS, 0, 0), midGame(0), UPGRADE_QUEUED_FRAME));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, build.upgradePriority(UpgradeType.Grooved_Spines,
                living(DEN_HYDRALISKS, 0, 0), midGame(0), UPGRADE_QUEUED_FRAME));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, build.upgradePriority(UpgradeType.Zerg_Missile_Attacks,
                living(MISSILE_HYDRALISKS, 0, 0), midGame(1), UPGRADE_QUEUED_FRAME));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, build.upgradePriority(UpgradeType.Zerg_Melee_Attacks,
                living(0, MELEE_ZERGLINGS, 0), midGame(1), UPGRADE_QUEUED_FRAME));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, build.upgradePriority(UpgradeType.Zerg_Flyer_Attacks,
                living(0, 0, FLYER_MUTALISKS), midGame(0), UPGRADE_QUEUED_FRAME));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, build.upgradePriority(UpgradeType.Zerg_Flyer_Carapace,
                living(0, 0, FLYER_MUTALISKS), midGame(0), UPGRADE_QUEUED_FRAME));
    }

    @Test
    void carapaceKeepsItsFramePriorityUntilTheArmyIsOut() {
        ThreeHatchMuta build = new ThreeHatchMuta();

        assertEquals(UPGRADE_QUEUED_FRAME, build.upgradePriority(UpgradeType.Zerg_Carapace,
                living(MISSILE_HYDRALISKS - 1, 0, 0), midGame(1), UPGRADE_QUEUED_FRAME));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, build.upgradePriority(UpgradeType.Zerg_Carapace,
                living(0, MISSILE_HYDRALISKS, 0), midGame(1), UPGRADE_QUEUED_FRAME));
    }

    @Test
    void overlordSpeedAndMetabolicBoostKeepTheirFramePriority() {
        ThreeHatchMuta build = new ThreeHatchMuta();
        UnitTypeCount count = living(MISSILE_HYDRALISKS, MELEE_ZERGLINGS, FLYER_MUTALISKS);

        assertEquals(UPGRADE_QUEUED_FRAME,
                build.upgradePriority(UpgradeType.Metabolic_Boost, count, midGame(2), UPGRADE_QUEUED_FRAME));
        assertEquals(UPGRADE_QUEUED_FRAME,
                build.upgradePriority(UpgradeType.Pneumatized_Carapace, count, midGame(2), UPGRADE_QUEUED_FRAME));
    }

    @Test
    void theHydraliskTargetIsZeroWithoutADen() {
        assertEquals(0, ThreeHatchMuta.desiredHydralisks(false, 0, SURPLUS_MINERALS, FLOAT_GAS, false, true));
    }

    @Test
    void theHydraliskTargetStaysAtTheBaseOnAnOrdinaryBank() {
        assertEquals(25, ThreeHatchMuta.BASE_HYDRALISKS);
        assertEquals(ThreeHatchMuta.BASE_HYDRALISKS,
                ThreeHatchMuta.desiredHydralisks(true, 25, SURPLUS_MINERALS - 1, FLOAT_GAS, false, true));
    }

    @Test
    void theHydraliskTargetRisesPastTheBaseOnSurplusMineralsAndGas() {
        assertEquals(26, ThreeHatchMuta.desiredHydralisks(true, 25, SURPLUS_MINERALS, FLOAT_GAS, false, true));
        assertEquals(41, ThreeHatchMuta.desiredHydralisks(true, 40, SURPLUS_MINERALS, FLOAT_GAS, false, true));
    }

    @Test
    void theHydraliskTargetDoesNotFloatBelowTheBase() {
        assertEquals(ThreeHatchMuta.BASE_HYDRALISKS,
                ThreeHatchMuta.desiredHydralisks(true, 3, SURPLUS_MINERALS, FLOAT_GAS, false, true));
    }

    @Test
    void theHydraliskTargetDoesNotFloatOnGasTheHiveBranchIsWaitingFor() {
        assertEquals(ThreeHatchMuta.BASE_HYDRALISKS,
                ThreeHatchMuta.desiredHydralisks(true, 25, 2000, FLOAT_GAS - 1, false, true));
    }

    @Test
    void theHydraliskTargetFloatsOnOneHydralisksGasOnceTheHiveStands() {
        int oneHydralisk = UnitType.Zerg_Hydralisk.gasPrice();

        assertEquals(26, ThreeHatchMuta.desiredHydralisks(true, 25, SURPLUS_MINERALS, oneHydralisk, true, true));
        assertEquals(ThreeHatchMuta.BASE_HYDRALISKS,
                ThreeHatchMuta.desiredHydralisks(true, 25, SURPLUS_MINERALS, oneHydralisk - 1, true, true));
    }

    @Test
    void theHydraliskTargetDoesNotFloatWhileDronesAreStillOwed() {
        assertEquals(ThreeHatchMuta.BASE_HYDRALISKS,
                ThreeHatchMuta.desiredHydralisks(true, 25, 2000, FLOAT_GAS, false, false));
    }

    @Test
    void gasUnitsAreWithheldWhileAPromotedUpgradeIsShortOfGas() {
        Plan muscularAugments = new UpgradePlan(UpgradeType.Muscular_Augments, BuildOrder.ARMY_UPGRADE_PRIORITY, 0);
        List<Plan> queue = Collections.singletonList(muscularAugments);

        assertTrue(ThreeHatchMuta.isGasHeldForResearch(queue, muscularAugments.gasPrice() - 1));
    }

    @Test
    void gasUnitsResumeOnceTheBankCoversTheUpgrade() {
        Plan muscularAugments = new UpgradePlan(UpgradeType.Muscular_Augments, BuildOrder.ARMY_UPGRADE_PRIORITY, 0);
        List<Plan> queue = Collections.singletonList(muscularAugments);

        assertFalse(ThreeHatchMuta.isGasHeldForResearch(queue, muscularAugments.gasPrice()));
    }

    @Test
    void anUpgradeStillAtItsFramePriorityHoldsNoGas() {
        Plan carapace = new UpgradePlan(UpgradeType.Zerg_Carapace, UPGRADE_QUEUED_FRAME, 0);

        assertFalse(ThreeHatchMuta.isGasHeldForResearch(Collections.singletonList(carapace), 0));
    }

    @Test
    void aQueuedUnitHoldsNoGas() {
        Plan hydralisk = new UnitPlan(UnitType.Zerg_Hydralisk, UnitPlan.ADVANCED_UNIT_PRIORITY);
        Plan drone = new UnitPlan(UnitType.Zerg_Drone, 3);

        assertFalse(ThreeHatchMuta.isGasHeldForResearch(Arrays.asList(hydralisk, drone), 0));
        assertFalse(ThreeHatchMuta.isGasHeldForResearch(Collections.<Plan>emptyList(), 0));
    }

    @Test
    void theHiveWaitsForOverlordSpeedToLeaveTheLair() {
        assertTrue(ThreeHatchMuta.shouldPlanHive(true, false));
        assertFalse(ThreeHatchMuta.shouldPlanHive(true, true));
        assertFalse(ThreeHatchMuta.shouldPlanHive(false, false));
    }

    @Test
    void aFloatedHydraliskLeavesTheHiveBranchItsGas() {
        int gasAfterOneHydralisk = ThreeHatchMuta.HYDRALISK_FLOAT_GAS - UnitType.Zerg_Hydralisk.gasPrice();

        assertTrue(gasAfterOneHydralisk >= GasBoundHiveTech.BRANCH_GAS);
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
