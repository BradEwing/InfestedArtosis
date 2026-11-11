package telemetry;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeMinRangeCellsTest {

    @Test
    void theColumnsAreAppendedAtTheEndOfTheHeader() {
        assertTrue(SquadDecisionLogger.HEADER.endsWith("sim_reentry_hold_strength,sim_siege_min_range_share,"
                + "sim_siege_covered_share,sim_siege_strength_main,sim_siege_strength_priced"));
    }

    @Test
    void aDecisionWithoutASiegedTankWritesMinusOne() {
        List<String> cells = SquadDecisionLogger.siegeMinRangeCells(new SquadDecision());

        assertEquals(Arrays.asList(Csv.format(-1), Csv.format(-1), Csv.format(-1), Csv.format(-1)), cells);
    }

    @Test
    void aDecisionWithASiegedTankWritesItsShares() {
        SquadDecision decision = new SquadDecision();
        decision.setSiegeMinRangeShare(0.5);
        decision.setSiegeCoveredShare(0.25);
        decision.setSiegeStrengthMain(8.0);
        decision.setSiegeStrengthPriced(4.0);

        assertEquals(Arrays.asList(Csv.format(0.5), Csv.format(0.25), Csv.format(8.0), Csv.format(4.0)),
                SquadDecisionLogger.siegeMinRangeCells(decision));
    }
}
