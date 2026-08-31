package macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionManagerTest {

    @Test
    void withholdsOverlordsWhileFreeSupplyExceedsTheCancelThreshold() {
        assertTrue(ProductionManager.hasExcessSupply(60, 20));
    }

    @Test
    void derivesOverlordsAtTheCancelThreshold() {
        assertFalse(ProductionManager.hasExcessSupply(37, 20));
        assertTrue(ProductionManager.hasExcessSupply(38, 20));
    }

    @Test
    void neverWithholdsOverlordsWhileSupplyIsTight() {
        for (int freeSupply = 0; freeSupply <= 17; freeSupply++) {
            assertFalse(ProductionManager.hasExcessSupply(200 + freeSupply, 200),
                    "free supply of " + freeSupply + " must still derive overlords");
        }
    }

    @Test
    void neverWithholdsTheEmergencyOverlordWhileSupplyBlocked() {
        for (int supply = 0; supply <= 400; supply++) {
            assertFalse(ProductionManager.hasExcessSupply(supply, supply),
                    "supply block at " + supply + " must still reach the emergency overlord");
        }
    }
}
