package telemetry;

import org.junit.jupiter.api.Test;
import strategy.buildorder.LarvaBoundMacroHatchery.Gate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanEventLoggerTest {

    private static final int PLAN_COLUMNS = 69;

    private static final boolean STARVED = true;

    private static final boolean NOT_STARVED = false;

    @Test
    void thePlanRowCarriesEveryColumnItsReadersIndexBy() {
        assertEquals(PLAN_COLUMNS, PlanEventLogger.PLAN_HEADER.split(",", -1).length);
        List<String> readByName = Arrays.asList("executor_unit_id", "builder_distance_px", "builder_at_site",
                "builder_dispatch_decision", "builder_role", "builder_order", "builder_in_range",
                "previous_executor_unit_id");
        for (String name : readByName) {
            assertTrue(indexOf(name) >= 0, name);
        }
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
    void theHiveTechGateColumnsFollowTheMacroHatcheryGate() {
        int techGate = indexOf("tech_gate");
        assertEquals(indexOf("macro_hatcheries_outstanding") + 1, techGate);
        assertEquals("gate_available_gas", column(techGate + 1));
        assertEquals("gate_required_gas", column(techGate + 2));
        assertEquals("extractors_completed", column(techGate + 3));
    }

    @Test
    void theFirstGateReadingOfTheGameIsAlwaysARow() {
        assertTrue(PlanEventLogger.isNewMacroHatcheryGateReading(Gate.TECH_NOT_READY, NOT_STARVED, null,
                NOT_STARVED));
    }

    @Test
    void aGateStandingThroughTheSameStateIsNotRepeated() {
        assertFalse(PlanEventLogger.isNewMacroHatcheryGateReading(Gate.OUTSTANDING, STARVED, Gate.OUTSTANDING,
                STARVED));
    }

    @Test
    void reachingANewGateIsARow() {
        assertTrue(PlanEventLogger.isNewMacroHatcheryGateReading(Gate.TRIGGER, STARVED, Gate.OUTSTANDING, STARVED));
    }

    /**
     * Game LMR9R0MB's 90 frame starvation run would carry no row at all if the gate alone were the
     * key: a build still short of its tech, or one whose macro hatchery is outstanding, answers the
     * same gate before the run and throughout it.
     */
    @Test
    void aStarvationRunBeginningUnderAStandingGateIsARow() {
        assertTrue(PlanEventLogger.isNewMacroHatcheryGateReading(Gate.TECH_NOT_READY, STARVED, Gate.TECH_NOT_READY,
                NOT_STARVED));
        assertTrue(PlanEventLogger.isNewMacroHatcheryGateReading(Gate.OUTSTANDING, STARVED, Gate.OUTSTANDING,
                NOT_STARVED));
    }

    @Test
    void leavingStarvationUnderAStandingGateIsARowSoTheNextRunIsMarkedAgain() {
        assertTrue(PlanEventLogger.isNewMacroHatcheryGateReading(Gate.OUTSTANDING, NOT_STARVED, Gate.OUTSTANDING,
                STARVED));
    }

    @Test
    void theBuilderThreatColumnsAreAppendedLast() {
        int route = indexOf("builder_route_enemies");
        assertEquals(indexOf("extractors_completed") + 1, route);
        assertEquals("builder_site_enemies", column(route + 1));
        assertEquals("builder_route_defense_zones", column(route + 2));
        assertEquals("builder_at_site", column(route + 3));
        assertEquals("builder_dispatch_decision", column(route + 4));
        assertEquals("lost_expansion_builders", column(route + 5));
        assertEquals("expansion_hold_until_frame", column(route + 6));
        assertEquals("builder_site_at_our_base", column(route + 7));
        assertEquals("builder_at_our_base", column(route + 8));
    }

    @Test
    void theBaseLostColumnFollowsTheBuilderThreatColumns() {
        assertEquals(indexOf("builder_at_our_base") + 1, indexOf("base_inner"));
    }

    @Test
    void theEnemyMainColumnsFollowTheBaseLostColumn() {
        int reason = indexOf("enemy_main_reason");
        assertEquals(indexOf("base_inner") + 1, reason);
        assertEquals("enemy_main_source_x", column(reason + 1));
        assertEquals("enemy_main_source_y", column(reason + 2));
    }

    @Test
    void theBuilderRoleColumnsAreAppendedLast() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        int role = indexOf("builder_role");
        assertEquals(indexOf("enemy_main_source_y") + 1, role);
        assertEquals("builder_order", column(role + 1));
        assertEquals("builder_in_range", column(role + 2));
        assertEquals("previous_executor_unit_id", column(role + 3));
        assertEquals("previous_executor_unit_id", columns[columns.length - 1]);
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
