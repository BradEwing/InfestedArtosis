package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanEventLoggerTest {

    private static final int PLAN_COLUMNS = 52;

    @Test
    void thePlanRowCarriesEveryColumnItsReadersIndexBy() {
        assertEquals(PLAN_COLUMNS, PlanEventLogger.PLAN_HEADER.split(",", -1).length);
    }

    @Test
    void theBlockerMineralPositionFollowsTheEnemyBarracksCount() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        assertEquals("enemy_barracks", columns[columns.length - 14]);
        assertEquals("blocker_mineral_x", columns[columns.length - 13]);
        assertEquals("blocker_mineral_y", columns[columns.length - 12]);
    }

    @Test
    void theEnemyGroundCountsAndYieldTargetFollowTheBlockerMineral() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        assertEquals("enemy_ground_known_at_bases", columns[columns.length - 11]);
        assertEquals("enemy_ground_visible_at_bases", columns[columns.length - 10]);
        assertEquals("yield_to_plan_id", columns[columns.length - 9]);
    }

    @Test
    void theMacroHatcheryGateColumnsFollowTheYieldTarget() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        assertEquals("macro_hatchery_gate", columns[columns.length - 8]);
        assertEquals("hatcheries", columns[columns.length - 7]);
        assertEquals("macro_tech_ready", columns[columns.length - 6]);
        assertEquals("macro_hatcheries_outstanding", columns[columns.length - 5]);
    }

    @Test
    void theHiveTechGateColumnsAreAppendedLast() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        assertEquals("tech_gate", columns[columns.length - 4]);
        assertEquals("gate_available_gas", columns[columns.length - 3]);
        assertEquals("gate_required_gas", columns[columns.length - 2]);
        assertEquals("extractors_completed", columns[columns.length - 1]);
    }
}
