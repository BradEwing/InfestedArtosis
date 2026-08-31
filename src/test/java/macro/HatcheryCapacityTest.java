package macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HatcheryCapacityTest {

    private static final int SATURATED_HATCHERIES = HatcheryCapacity.EXCESS_HATCHERIES;

    private static final int IDLE_LARVA = HatcheryCapacity.EXCESS_LARVA;

    @Test
    void hatcheriesAreExcessOnceTheirLarvaGoUnspent() {
        assertTrue(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA, false));
    }

    @Test
    void hatcheriesAreNotExcessWhileLarvaAreSpent() {
        assertFalse(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA - 1, false));
    }

    @Test
    void hatcheriesAreNotExcessBelowTheHatcheryFloor() {
        assertFalse(HatcheryCapacity.isExcess(SATURATED_HATCHERIES - 1, IDLE_LARVA * 2, false));
    }

    @Test
    void floatingMineralsOverrideIdleLarva() {
        assertFalse(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA, true));
    }

    @Test
    void weAreNotBehindOnHatcheriesWeCannotKeepBusy() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA, false);

        assertFalse(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess));
    }

    @Test
    void weAreBehindWhenTheEnemyOutExpandsUsAndOurLarvaAreSpent() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA - 1, false);

        assertTrue(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess));
    }

    /**
     * No count below SATURATED_HATCHERIES is excess. Parity therefore always expands from one or
     * two bases.
     */
    @Test
    void parityStillExpandsFromOneAndTwoBases() {
        for (int ourTotal = 1; ourTotal < SATURATED_HATCHERIES; ourTotal++) {
            boolean excess = HatcheryCapacity.isExcess(ourTotal, IDLE_LARVA * 2, false);

            assertTrue(HatcheryCapacity.isBehind(ourTotal, ourTotal + 1, excess));
        }
    }

    @Test
    void weAreNotBehindAtParity() {
        assertFalse(HatcheryCapacity.isBehind(2, 2, false));
    }

    @Test
    void floatingMineralsStillExpandAtSaturation() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA, true);

        assertTrue(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess));
    }

    @Test
    void theTwoRulesNeverDisagree() {
        for (int hatcheries = 0; hatcheries <= 6; hatcheries++) {
            for (int larva = 0; larva <= 12; larva++) {
                boolean excess = HatcheryCapacity.isExcess(hatcheries, larva, false);
                boolean behind = HatcheryCapacity.isBehind(hatcheries, hatcheries + 1, excess);

                assertFalse(excess && behind);
            }
        }
    }
}
