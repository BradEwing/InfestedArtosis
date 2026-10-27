package strategy.buildorder.protoss;

import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtossReactionsTest {

    private static final Time EARLY = new Time(2, 30);

    private static final Time AFTER_THREE_TWENTY = new Time(3, 21);

    private static final Time LATE = new Time(10, 1);

    @Test
    void twoGateWithAZealotSeenAsksForOneSunken() {
        assertEquals(1, ProtossReactions.matchupSunkens(false, true, 1, EARLY, 9));
    }

    @Test
    void twoGateAloneWaitsForAZealotOrThreeTwenty() {
        assertEquals(0, ProtossReactions.matchupSunkens(false, true, 0, EARLY, 9));
        assertEquals(1, ProtossReactions.matchupSunkens(false, true, 0, AFTER_THREE_TWENTY, 9));
    }

    @Test
    void moreThanThreeAndMoreThanSixZealotsEachAddOne() {
        assertEquals(1, ProtossReactions.matchupSunkens(false, false, 4, EARLY, 9));
        assertEquals(2, ProtossReactions.matchupSunkens(false, false, 7, EARLY, 9));
        assertEquals(3, ProtossReactions.matchupSunkens(false, true, 7, EARLY, 9));
    }

    @Test
    void cannonRushAsksForExactlyOneWhateverElseIsSeen() {
        assertEquals(1, ProtossReactions.matchupSunkens(true, true, 9, LATE, 30));
    }

    @Test
    void aLargeLateEconomyAddsOne() {
        assertEquals(1, ProtossReactions.matchupSunkens(false, false, 0, LATE, 21));
        assertEquals(0, ProtossReactions.matchupSunkens(false, false, 0, LATE, 20));
    }

    @Test
    void zerglingTargetStartsFromTheDetectedStrategy() {
        assertEquals(6, ProtossReactions.zerglingTarget(false, 0, 0, false, false, false, 0, 0));
        assertEquals(12, ProtossReactions.zerglingTarget(false, 0, 0, true, false, false, 0, 0));
        assertEquals(4, ProtossReactions.zerglingTarget(false, 0, 0, false, true, false, 0, 0));
        assertEquals(2, ProtossReactions.zerglingTarget(false, 0, 0, false, false, true, 0, 0));
    }

    @Test
    void zerglingTargetAddsTheZealotDrivenZerglings() {
        assertEquals(12 + 8, ProtossReactions.zerglingTarget(false, 0, 0, true, false, false, 4, 2));
        assertEquals(12 + ProtossReactions.zealotDrivenZerglings(20, 2),
                ProtossReactions.zerglingTarget(false, 0, 0, true, false, false, 20, 2));
    }

    @Test
    void cannonRushScalesWithCannons() {
        assertEquals(8 + 6, ProtossReactions.zerglingTarget(true, 2, 0, false, false, false, 0, 0));
    }

    @Test
    void protossBaseDelegatesToTheSharedHelper() {
        for (int zealots = 0; zealots <= 30; zealots++) {
            for (int gateways = 0; gateways <= 8; gateways++) {
                assertEquals(ProtossReactions.zealotDrivenZerglings(zealots, gateways),
                        ProtossBase.zealotDrivenZerglings(zealots, gateways));
            }
        }
    }
}
