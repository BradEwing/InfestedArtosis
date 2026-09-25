package strategy.buildorder.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.plan.Plan;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.LarvaBoundMacroHatchery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchLurkerTest {

    private static final int BASE_TARGET = 8;

    private static final int NO_LARVA = 0;

    private static final int TWO_HATCHERIES = 2;

    private static final int NO_ENEMIES = 0;

    private static final int NO_MACRO_HATCHERY = 0;

    /** Game LC0QF0B5: the frame the larva-bound request without its Spire term would first have fired. */
    private static final int FLOAT_FRAME = 9386;

    private static final TilePosition MAIN_TILE = new TilePosition(117, 119);

    /**
     * Game LC0QF0B5 at 6:31: Lair and Hydralisk Den finished, two hatcheries, no larva, and both
     * unreserved banks over the float bars. The build's own third hatchery gates never opened.
     */
    @Test
    void requestsOneMainMacroHatcheryWithTheLairAndDenFinished() {
        TechProgression techProgression = lairAndDen();

        assertTrue(requestsMacroHatchery(techProgression, NO_ENEMIES, NO_MACRO_HATCHERY));

        Plan plan = BuildOrder.macroHatcheryPlan(FLOAT_FRAME, MAIN_TILE);
        assertEquals(UnitType.Zerg_Hatchery, plan.getPlannedUnit());
        assertTrue(plan.isMacroHatchery());
        assertEquals(MAIN_TILE, plan.getBuildPosition());
    }

    @Test
    void requestsAMacroHatcheryWithLurkerAspectResearched() {
        TechProgression techProgression = new TechProgression();
        techProgression.setLair(true);
        techProgression.setLurker(true);

        assertTrue(requestsMacroHatchery(techProgression, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileTheDenIsStillMorphing() {
        TechProgression techProgression = new TechProgression();
        techProgression.setLair(true);
        techProgression.setPlannedDen(true);

        assertFalse(requestsMacroHatchery(techProgression, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileTheLairIsStillMorphing() {
        TechProgression techProgression = new TechProgression();
        techProgression.setPlannedLair(true);
        techProgression.setHydraliskDen(true);

        assertFalse(requestsMacroHatchery(techProgression, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileEnemiesAreKnownAtOurBases() {
        assertFalse(requestsMacroHatchery(lairAndDen(), 1, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestASecondMacroHatcheryWhileOneIsOutstanding() {
        assertFalse(requestsMacroHatchery(lairAndDen(), NO_ENEMIES, 1));
    }

    @Test
    void theTargetStandsWhileEnoughHydralisksExistToReachIt() {
        assertEquals(BASE_TARGET, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 0, 8));
        assertEquals(BASE_TARGET, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 3, 12));
    }

    @Test
    void theTargetFallsToWhatTheProducersCanReach() {
        assertEquals(2, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 0, 2));
        assertEquals(5, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 3, 2));
    }

    @Test
    void noHydraliskAndNoLurkerAsksForNothing() {
        assertEquals(0, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 0, 0));
    }

    @Test
    void lurkersAlreadyOnTheFieldCountTowardsTheTarget() {
        assertEquals(BASE_TARGET, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 8, 0));
    }

    @Test
    void thirdHatchWaitsForTwoFieldedLurkers() {
        assertFalse(ThreeHatchLurker.hasFieldedLurkersForThirdHatch(0));
        assertFalse(ThreeHatchLurker.hasFieldedLurkersForThirdHatch(1));
        assertTrue(ThreeHatchLurker.hasFieldedLurkersForThirdHatch(2));
    }

    @Test
    void metabolicBoostWaitsForFieldedLurkers() {
        assertFalse(ThreeHatchLurker.shouldPlanMetabolicBoost(12, 2));
        assertTrue(ThreeHatchLurker.shouldPlanMetabolicBoost(12, 3));
    }

    @Test
    void metabolicBoostWaitsForTwelveZerglings() {
        assertFalse(ThreeHatchLurker.shouldPlanMetabolicBoost(11, 3));
        assertTrue(ThreeHatchLurker.shouldPlanMetabolicBoost(12, 3));
    }

    @Test
    void metabolicBoostWithholdsWhileTheZerglingsAreOnlyPlanned() {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < 6; i++) {
            count.planUnit(UnitType.Zerg_Zergling);
        }

        assertTrue(count.get(UnitType.Zerg_Zergling) >= 12);
        assertFalse(ThreeHatchLurker.shouldPlanMetabolicBoost(count.livingCount(UnitType.Zerg_Zergling), 3));
    }

    @Test
    void groovedSpinesWithholdsWhileTheHydralisksAreOnlyPlanned() {
        UnitTypeCount count = hydralisks(7, 0);

        assertTrue(count.get(UnitType.Zerg_Hydralisk) > 6);
        assertFalse(ThreeHatchLurker.shouldPlanGroovedSpines(count.livingCount(UnitType.Zerg_Hydralisk)));
    }

    @Test
    void groovedSpinesWithholdsWithSixLivingHydralisksAndMorePlanned() {
        UnitTypeCount count = hydralisks(7, 6);

        assertFalse(ThreeHatchLurker.shouldPlanGroovedSpines(count.livingCount(UnitType.Zerg_Hydralisk)));
    }

    @Test
    void groovedSpinesPlansWithSevenLivingHydralisks() {
        assertTrue(ThreeHatchLurker.shouldPlanGroovedSpines(7));
    }

    @Test
    void muscularAugmentsWithholdsWhileTheHydralisksAreOnlyPlanned() {
        UnitTypeCount count = hydralisks(4, 3);

        assertTrue(count.get(UnitType.Zerg_Hydralisk) > 3);
        assertFalse(ThreeHatchLurker.shouldPlanMuscularAugments(count.livingCount(UnitType.Zerg_Hydralisk), 2));
    }

    @Test
    void muscularAugmentsPlansWithFourLivingHydralisksAndTwoLurkers() {
        assertTrue(ThreeHatchLurker.shouldPlanMuscularAugments(4, 2));
    }

    @Test
    void muscularAugmentsWaitsForFieldedLurkers() {
        assertFalse(ThreeHatchLurker.shouldPlanMuscularAugments(4, 1));
    }

    private static TechProgression lairAndDen() {
        TechProgression techProgression = new TechProgression();
        techProgression.setLair(true);
        techProgression.setHydraliskDen(true);
        return techProgression;
    }

    private static boolean requestsMacroHatchery(TechProgression techProgression, int enemiesAtBases,
                                                 int outstandingMacroHatcheries) {
        return LarvaBoundMacroHatchery.shouldPlan(LarvaBoundMacroHatchery.isLurkerTechReady(techProgression),
                NO_LARVA, TWO_HATCHERIES, LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS,
                enemiesAtBases, outstandingMacroHatcheries);
    }

    private static UnitTypeCount hydralisks(int planned, int living) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < planned; i++) {
            count.planUnit(UnitType.Zerg_Hydralisk);
        }
        for (int i = 0; i < living; i++) {
            count.addUnit(UnitType.Zerg_Hydralisk);
        }
        return count;
    }

    private static final int UPGRADE_QUEUED_FRAME = 9000;

    private static UnitTypeCount livingHydralisksAndLurkers(int hydralisks, int lurkers) {
        UnitTypeCount count = hydralisks(0, hydralisks);
        for (int i = 0; i < lurkers; i++) {
            count.addUnit(UnitType.Zerg_Lurker);
        }
        return count;
    }

    private static int upgradePriority(UpgradeType upgradeType, int hydralisks, int lurkers) {
        TechProgression techProgression = new TechProgression();
        techProgression.setHydraliskDen(true);
        techProgression.setEvolutionChambers(1);
        return new ThreeHatchLurker().upgradePriority(upgradeType,
                livingHydralisksAndLurkers(hydralisks, lurkers), techProgression, UPGRADE_QUEUED_FRAME);
    }

    @Test
    void hydraliskDenUpgradesCountHydralisksAndLurkersTogether() {
        int trigger = ThreeHatchLurker.HYDRALISKS_AND_LURKERS_BEFORE_DEN_UPGRADE_PRIORITY;

        assertEquals(UPGRADE_QUEUED_FRAME, upgradePriority(UpgradeType.Muscular_Augments, trigger - 3, 2));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Muscular_Augments, trigger - 2, 2));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Grooved_Spines, trigger - 1, 2));
    }

    @Test
    void evolutionUpgradesMoveAtTheirOwnTrigger() {
        int trigger = ThreeHatchLurker.HYDRALISKS_AND_LURKERS_BEFORE_EVOLUTION_UPGRADE_PRIORITY;

        assertEquals(UPGRADE_QUEUED_FRAME, upgradePriority(UpgradeType.Zerg_Missile_Attacks, trigger - 1, 0));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Zerg_Carapace, trigger, 0));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Zerg_Missile_Attacks, trigger - 4, 5));
    }
}
