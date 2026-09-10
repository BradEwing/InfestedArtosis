package strategy.buildorder.opener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwelvePoolTest {

    @Test
    void handsOffOnASpawningPoolUnderConstruction() {
        assertTrue(TwelvePool.openerComplete(1, 11));
    }

    @Test
    void holdsWhileNoSpawningPoolIsCommittedTo() {
        assertFalse(TwelvePool.openerComplete(0, 11));
    }

    @Test
    void handsOffOnTwelveLivingDronesWithoutASpawningPool() {
        assertTrue(TwelvePool.openerComplete(0, 12));
    }
}
