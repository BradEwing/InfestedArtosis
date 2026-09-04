package macro;

import bwapi.UnitType;
import info.UnitTypeCount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionsTest {

    private static final boolean WITHIN_RUSH_WINDOW = true;

    private static final int QUEUED_ZERGLING_PLANS = 6;

    private static UnitTypeCount withQueuedZerglingPlans(int plans) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < plans; i++) {
            count.planUnit(UnitType.Zerg_Zergling);
        }
        return count;
    }

    private static UnitTypeCount withLivingZerglings(int zerglings) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < zerglings; i++) {
            count.addUnit(UnitType.Zerg_Zergling);
        }
        return count;
    }

    @Test
    void aQueuedZerglingPlanIsTwoFutureZerglingsAndNoLivingOne() {
        UnitTypeCount count = withQueuedZerglingPlans(QUEUED_ZERGLING_PLANS);

        assertEquals(2 * QUEUED_ZERGLING_PLANS, count.get(UnitType.Zerg_Zergling));
        assertEquals(0, count.livingCount(UnitType.Zerg_Zergling));
    }

    @Test
    void staysArmedWhileAFullZerglingQueueHasHatchedNothing() {
        UnitTypeCount count = withQueuedZerglingPlans(QUEUED_ZERGLING_PLANS);

        assertTrue(Reactions.isPreparingForEarlyRush(WITHIN_RUSH_WINDOW, count.livingCount(UnitType.Zerg_Zergling)));
        assertFalse(Reactions.isPreparingForEarlyRush(WITHIN_RUSH_WINDOW, count.get(UnitType.Zerg_Zergling)));
    }

    @Test
    void standsDownOnceTheSafeZerglingCountHatches() {
        UnitTypeCount count = withLivingZerglings(Reactions.EARLY_RUSH_SAFE_ZERGLINGS);

        assertFalse(Reactions.isPreparingForEarlyRush(WITHIN_RUSH_WINDOW, count.livingCount(UnitType.Zerg_Zergling)));
    }

    @Test
    void standsDownOutsideTheRushWindow() {
        assertFalse(Reactions.isPreparingForEarlyRush(false, 0));
    }

    @Test
    void cutsDronesWhileAFullZerglingQueueHasHatchedNothing() {
        UnitTypeCount count = withQueuedZerglingPlans(QUEUED_ZERGLING_PLANS);

        assertTrue(Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR, count.livingCount(UnitType.Zerg_Zergling)));
        assertFalse(Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR, count.get(UnitType.Zerg_Zergling)));
    }

    @Test
    void holdsTheDroneCutBelowTheDroneFloor() {
        assertFalse(Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR - 1, 0));
    }

    @Test
    void queuedDronesDoNotReachTheDroneFloor() {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < Reactions.EARLY_RUSH_DRONE_FLOOR; i++) {
            count.planUnit(UnitType.Zerg_Drone);
        }

        assertFalse(Reactions.shouldCutDrones(count.livingCount(UnitType.Zerg_Drone), 0));
        assertTrue(Reactions.shouldCutDrones(count.get(UnitType.Zerg_Drone), 0));
    }

    @Test
    void releasesTheDroneCutOnceTheZerglingsHatch() {
        UnitTypeCount count = withLivingZerglings(Reactions.EARLY_RUSH_CUT_ZERGLINGS);

        assertFalse(Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR, count.livingCount(UnitType.Zerg_Zergling)));
    }
}
