package unit.managed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegroupTest {

    @Test
    void aUnitFarFromItsRegroupPointKeepsFlyingThereUntilTheFrameLimit() {
        assertTrue(ManagedUnit.regroupHolds(ManagedUnit.REGROUP_ARRIVAL_DISTANCE + 1, 1000, 1000));
        assertFalse(ManagedUnit.regroupHolds(ManagedUnit.REGROUP_ARRIVAL_DISTANCE + 1, 1001, 1000));
    }

    @Test
    void aUnitAtItsRegroupPointTakesItsSquadsOrdersAgain() {
        assertFalse(ManagedUnit.regroupHolds(ManagedUnit.REGROUP_ARRIVAL_DISTANCE, 10, 1000));
        assertFalse(ManagedUnit.regroupHolds(0, 10, 1000));
    }
}
