package strategy.buildorder.terran;

import bwapi.UnitType;
import info.UnitTypeCount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchLurkerTest {

    private static final int BASE_TARGET = 8;

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
}
