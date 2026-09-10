package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanEventLoggerTest {

    private static final int PLAN_COLUMNS = 38;

    @Test
    void thePlanRowCarriesEveryColumnItsReadersIndexBy() {
        assertEquals(PLAN_COLUMNS, PlanEventLogger.PLAN_HEADER.split(",", -1).length);
    }

    @Test
    void theCumulativeGasTotalIsTheLastColumn() {
        String[] columns = PlanEventLogger.PLAN_HEADER.split(",", -1);
        assertEquals("gas_gathered", columns[columns.length - 1]);
    }
}
