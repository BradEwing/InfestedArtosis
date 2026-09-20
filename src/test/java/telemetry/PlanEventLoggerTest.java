package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanEventLoggerTest {

    private static final int PLAN_COLUMNS = 57;

    @Test
    void thePlanRowCarriesEveryColumnItsReadersIndexBy() {
        assertEquals(PLAN_COLUMNS, PlanEventLogger.PLAN_HEADER.split(",", -1).length);
    }

    @Test
    void theBlockerMineralPositionFollowsTheEnemyBarracksCount() {
        int barracks = indexOf("enemy_barracks");
        assertEquals("blocker_mineral_x", column(barracks + 1));
        assertEquals("blocker_mineral_y", column(barracks + 2));
    }

    @Test
    void theEnemyGroundCountsAndYieldTargetFollowTheBlockerMineral() {
        int known = indexOf("enemy_ground_known_at_bases");
        assertEquals(indexOf("blocker_mineral_y") + 1, known);
        assertEquals("enemy_ground_visible_at_bases", column(known + 1));
        assertEquals("yield_to_plan_id", column(known + 2));
    }

    @Test
    void theMacroHatcheryGateColumnsFollowTheYieldTarget() {
        int gate = indexOf("macro_hatchery_gate");
        assertEquals(indexOf("yield_to_plan_id") + 1, gate);
        assertEquals("hatcheries", column(gate + 1));
        assertEquals("macro_tech_ready", column(gate + 2));
        assertEquals("macro_hatcheries_outstanding", column(gate + 3));
    }

    @Test
    void theBuilderThreatColumnsAreAppendedLast() {
        int route = indexOf("builder_route_enemies");
        assertEquals(indexOf("macro_hatcheries_outstanding") + 1, route);
        assertEquals("builder_site_enemies", column(route + 1));
        assertEquals("builder_route_defense_zones", column(route + 2));
        assertEquals("builder_at_site", column(route + 3));
        assertEquals("builder_dispatch_decision", column(route + 4));
        assertEquals("lost_expansion_builders", column(route + 5));
        assertEquals("expansion_hold_until_frame", column(route + 6));
        assertEquals("builder_site_at_our_base", column(route + 7));
        assertEquals("builder_at_our_base", column(route + 8));
    }

    private static String column(int index) {
        return PlanEventLogger.PLAN_HEADER.split(",", -1)[index];
    }

    private static int indexOf(String name) {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
