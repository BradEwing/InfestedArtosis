package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanEventLoggerTest {

    private static final int PLAN_COLUMNS = 44;

    @Test
    void thePlanRowCarriesEveryColumnItsReadersIndexBy() {
        assertEquals(PLAN_COLUMNS, PlanEventLogger.PLAN_HEADER.split(",", -1).length);
    }

    @Test
    void theBlockerMineralPositionFollowsTheEnemyBarracksCount() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        assertEquals("enemy_barracks", columns[columns.length - 6]);
        assertEquals("blocker_mineral_x", columns[columns.length - 5]);
        assertEquals("blocker_mineral_y", columns[columns.length - 4]);
    }

    @Test
    void theEnemyGroundCountsAndYieldTargetAreAppendedLast() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        assertEquals("enemy_ground_known_at_bases", columns[columns.length - 3]);
        assertEquals("enemy_ground_visible_at_bases", columns[columns.length - 2]);
        assertEquals("yield_to_plan_id", columns[columns.length - 1]);
    }
}
