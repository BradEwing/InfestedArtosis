package strategy.buildorder.opener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverpoolTest {

    @Test
    void handsOffOnceThePoolIsCommittedAndTheZerglingsAreStarted() {
        assertTrue(Overpool.openerComplete(1, Overpool.ZERGLING_TARGET));
    }

    @Test
    void holdsWhileTheZerglingsAreNotStarted() {
        assertFalse(Overpool.openerComplete(1, Overpool.ZERGLING_TARGET - 2));
    }

    @Test
    void holdsWhileNoSpawningPoolIsCommittedTo() {
        assertFalse(Overpool.openerComplete(0, Overpool.ZERGLING_TARGET));
    }
}
