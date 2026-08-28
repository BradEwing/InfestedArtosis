package telemetry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplyQuantilesTest {

    private static List<int[]> arrivals(int... offsetsAndWeights) {
        List<int[]> arrivals = new ArrayList<>();
        for (int i = 0; i < offsetsAndWeights.length; i += 2) {
            arrivals.add(new int[] {offsetsAndWeights[i], offsetsAndWeights[i + 1]});
        }
        return arrivals;
    }

    @Test
    void emptyArrivalsReturnNotApplicable() {
        assertEquals(-1, SupplyQuantiles.weighted(Collections.<int[]>emptyList(), 0.5));
    }

    @Test
    void zeroTotalWeightReturnsNotApplicable() {
        assertEquals(-1, SupplyQuantiles.weighted(arrivals(0, 0, 100, 0), 0.5));
    }

    @Test
    void aFullyCommittedArmyHasZeroOffsetsAtEveryQuantile() {
        List<int[]> committed = arrivals(0, 1, 0, 1, 0, 1, 0, 1);
        assertEquals(0, SupplyQuantiles.weighted(committed, 0.25));
        assertEquals(0, SupplyQuantiles.weighted(committed, 0.50));
        assertEquals(0, SupplyQuantiles.weighted(committed, 0.75));
    }

    @Test
    void aTricklingArmySpreadsTheQuantiles() {
        List<int[]> trickle = arrivals(0, 1, 100, 1, 200, 1, 300, 1);
        assertEquals(0, SupplyQuantiles.weighted(trickle, 0.25));
        assertEquals(100, SupplyQuantiles.weighted(trickle, 0.50));
        assertEquals(200, SupplyQuantiles.weighted(trickle, 0.75));
    }

    @Test
    void heavierUnitsPullTheQuantilesTowardTheirOwnArrival() {
        List<int[]> mixed = arrivals(0, 1, 240, 8);
        assertEquals(240, SupplyQuantiles.weighted(mixed, 0.50));
    }

    @Test
    void unsortedInputIsHandled() {
        List<int[]> unsorted = arrivals(300, 1, 0, 1, 200, 1, 100, 1);
        assertEquals(100, SupplyQuantiles.weighted(unsorted, 0.50));
    }

    @Test
    void theInputListIsNotReordered() {
        List<int[]> unsorted = arrivals(300, 1, 0, 1);
        SupplyQuantiles.weighted(unsorted, 0.5);
        assertEquals(300, unsorted.get(0)[0]);
        assertEquals(0, unsorted.get(1)[0]);
    }

    @Test
    void quantilesNeverExceedTheLatestArrival() {
        List<int[]> spread = arrivals(0, 1, 480, 1);
        assertEquals(480, SupplyQuantiles.weighted(spread, 1.0));
        assertEquals(0, SupplyQuantiles.weighted(spread, 0.0));
    }
}
