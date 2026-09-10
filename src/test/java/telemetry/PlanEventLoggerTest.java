package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanEventLoggerTest {

    private static final int PLAN_COLUMNS = 39;

    @Test
    void thePlanRowCarriesEveryColumnItsReadersIndexBy() {
        assertEquals(PLAN_COLUMNS, PlanEventLogger.PLAN_HEADER.split(",", -1).length);
    }

    @Test
    void theEnemyBarracksCountIsTheLastColumn() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        assertEquals("enemy_barracks", columns[columns.length - 1]);
    }
}
