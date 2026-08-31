package macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyCapacityTest {

    @Test
    void withholdsOverlordsWhileFreeSupplyExceedsTheCancelThreshold() {
        assertTrue(SupplyCapacity.isExcess(60, 20));
    }

    @Test
    void derivesOverlordsAtTheCancelThreshold() {
        assertFalse(SupplyCapacity.isExcess(37, 20));
        assertTrue(SupplyCapacity.isExcess(38, 20));
    }

    @Test
    void neverWithholdsOverlordsWhileSupplyIsTight() {
        for (int freeSupply = 0; freeSupply <= 17; freeSupply++) {
            assertFalse(SupplyCapacity.isExcess(200 + freeSupply, 200),
                    "free supply of " + freeSupply + " must still derive overlords");
        }
    }

    @Test
    void neverWithholdsTheEmergencyOverlordWhileSupplyBlocked() {
        for (int supply = 0; supply <= 400; supply++) {
            assertFalse(SupplyCapacity.isExcess(supply, supply),
                    "supply block at " + supply + " must still reach the emergency overlord");
        }
    }
}
