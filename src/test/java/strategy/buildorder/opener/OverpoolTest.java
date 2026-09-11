package strategy.buildorder.opener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverpoolTest {

    @Test
    void handsOffOnASpawningPoolUnderConstruction() {
        assertTrue(Overpool.openerComplete(1));
    }

    @Test
    void holdsWhileNoSpawningPoolIsCommittedTo() {
        assertFalse(Overpool.openerComplete(0));
    }
}
