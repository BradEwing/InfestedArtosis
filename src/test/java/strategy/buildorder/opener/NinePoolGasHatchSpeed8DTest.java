package strategy.buildorder.opener;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinePoolGasHatchSpeed8DTest {

    private static final String NAME = "9PoolGasHatchSpeed8D";

    private static final int START_LOCATIONS = 4;

    private static final int NO_POOL = 0;

    private static final int ONE_POOL = 1;

    private static final int EIGHT_SUPPLY = 16;

    private static final int NINE_SUPPLY = 18;

    private static final int SEVENTEEN_SUPPLY = 34;

    private static final int NO_EXTRACTOR = 0;

    private static final int ONE_EXTRACTOR = 1;

    private static final int NONE_QUEUED = 0;

    private static final Time FIVE_MINUTES = new Time(5, 0);

    @Test
    void isAZvZOpenerOnly() {
        NinePoolGasHatchSpeed8D opener = new NinePoolGasHatchSpeed8D();

        assertEquals(NAME, opener.getName());
        assertTrue(opener.isOpener());
        assertTrue(opener.playsRace(Race.Zerg));
        assertFalse(opener.playsRace(Race.Terran));
        assertFalse(opener.playsRace(Race.Protoss));
        assertFalse(opener.playsRace(Race.Unknown));
    }

    @Test
    void theFactoryOffersItAgainstZergOnly() {
        BuildOrderFactory zerg = new BuildOrderFactory(START_LOCATIONS, Race.Zerg);
        assertTrue(zerg.getOpenerNames().contains(NAME));
        assertTrue(zerg.isPlayableOpener(zerg.getByName(NAME)));

        for (Race race : new Race[]{Race.Terran, Race.Protoss, Race.Unknown}) {
            BuildOrderFactory factory = new BuildOrderFactory(START_LOCATIONS, race);
            assertFalse(factory.getOpenerNames().contains(NAME), race.toString());
            assertEquals(NAME, factory.getByName(NAME).getName(), race.toString());
        }
    }

    @Test
    void theOpeningTakesDronesToNine() {
        assertTrue(NinePoolGasHatchSpeed8D.shouldPlanOpeningDrone(8, true));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanOpeningDrone(9, true));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanOpeningDrone(8, false));
    }

    @Test
    void theTrickWaitsForAStandingPoolAndFullSupply() {
        assertEquals(NinePoolGasHatchSpeed8D.TrickStart.WAIT,
                NinePoolGasHatchSpeed8D.trickStart(NO_POOL, NINE_SUPPLY, NINE_SUPPLY, true));
        assertEquals(NinePoolGasHatchSpeed8D.TrickStart.WAIT,
                NinePoolGasHatchSpeed8D.trickStart(ONE_POOL, EIGHT_SUPPLY, NINE_SUPPLY, true));
        assertEquals(NinePoolGasHatchSpeed8D.TrickStart.ARM,
                NinePoolGasHatchSpeed8D.trickStart(ONE_POOL, NINE_SUPPLY, NINE_SUPPLY, true));
    }

    @Test
    void theTrickIsSkippedWhenItCannotFreeNeededSupplyOrClaimAGeyser() {
        assertEquals(NinePoolGasHatchSpeed8D.TrickStart.SKIP,
                NinePoolGasHatchSpeed8D.trickStart(ONE_POOL, NINE_SUPPLY, NINE_SUPPLY, false));
        assertEquals(NinePoolGasHatchSpeed8D.TrickStart.SKIP,
                NinePoolGasHatchSpeed8D.trickStart(ONE_POOL, NINE_SUPPLY, SEVENTEEN_SUPPLY, true));
    }

    @Test
    void theDroneCapHoldsAtEightOnceTheHatcheryIsQueued() {
        assertEquals(8, NinePoolGasHatchSpeed8D.DRONE_CAP);
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanCapDrone(false, 7, true));
        assertTrue(NinePoolGasHatchSpeed8D.shouldPlanCapDrone(true, 7, true));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanCapDrone(true, 8, true));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanCapDrone(true, 9, true));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanCapDrone(true, 7, false));
    }

    @Test
    void zerglingsWaitOnAFinishedPoolAndStopAtTheStepTotal() {
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanZergling(NO_POOL, 0,
                NinePoolGasHatchSpeed8D.FIRST_ZERGLING_PLANS, NONE_QUEUED));
        assertTrue(NinePoolGasHatchSpeed8D.shouldPlanZergling(ONE_POOL, 0,
                NinePoolGasHatchSpeed8D.FIRST_ZERGLING_PLANS, NONE_QUEUED));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanZergling(ONE_POOL,
                NinePoolGasHatchSpeed8D.FIRST_ZERGLING_PLANS, NinePoolGasHatchSpeed8D.FIRST_ZERGLING_PLANS,
                NONE_QUEUED));
    }

    @Test
    void theZerglingFloodKeepsTheQueueShort() {
        int flood = NinePoolGasHatchSpeed8D.TOTAL_ZERGLING_PLANS;
        assertTrue(NinePoolGasHatchSpeed8D.shouldPlanZergling(ONE_POOL, 10, flood,
                NinePoolGasHatchSpeed8D.MAX_QUEUED_ZERGLING_PLANS - 1));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanZergling(ONE_POOL, 10, flood,
                NinePoolGasHatchSpeed8D.MAX_QUEUED_ZERGLING_PLANS));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanZergling(ONE_POOL, flood, flood, NONE_QUEUED));
    }

    @Test
    void queuesFortyZerglingPairsInAll() {
        assertEquals(40, NinePoolGasHatchSpeed8D.TOTAL_ZERGLING_PLANS);
    }

    @Test
    void theRealExtractorComesOnceAndNeverAfterTheGasIsTaken() {
        assertTrue(NinePoolGasHatchSpeed8D.shouldPlanExtractor(false, NO_EXTRACTOR, true));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanExtractor(false, ONE_EXTRACTOR, true));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanExtractor(false, NO_EXTRACTOR, false));
        assertFalse(NinePoolGasHatchSpeed8D.shouldPlanExtractor(true, NO_EXTRACTOR, true));
    }

    @Test
    void handsOverOnceEveryZerglingPairIsQueued() {
        assertFalse(NinePoolGasHatchSpeed8D.openerComplete(
                NinePoolGasHatchSpeed8D.TOTAL_ZERGLING_PLANS - 1, false, FIVE_MINUTES));
        assertTrue(NinePoolGasHatchSpeed8D.openerComplete(
                NinePoolGasHatchSpeed8D.TOTAL_ZERGLING_PLANS, false, FIVE_MINUTES));
    }

    @Test
    void handsOverAsSoonAsTheZerglingFloodHoldStands() {
        assertTrue(NinePoolGasHatchSpeed8D.openerComplete(NinePoolGasHatchSpeed8D.FIRST_ZERGLING_PLANS, true,
                new Time(2, 49)));
        assertTrue(NinePoolGasHatchSpeed8D.openerComplete(0, true, new Time(1, 0)));
    }

    @Test
    void handsOverAtTheDeadlineWhenZerglingsAreStarved() {
        int starved = 34;
        assertFalse(NinePoolGasHatchSpeed8D.openerComplete(starved, false,
                NinePoolGasHatchSpeed8D.HAND_OVER_DEADLINE));
        assertTrue(NinePoolGasHatchSpeed8D.openerComplete(starved, false,
                new Time(NinePoolGasHatchSpeed8D.HAND_OVER_DEADLINE.getFrames() + 1)));
        assertTrue(NinePoolGasHatchSpeed8D.openerComplete(starved, false, new Time(12, 9)));
    }

    @Test
    void aCapDroneOutranksTheZerglingPairsAndStaysBehindTheEmergencyBand() {
        assertTrue(NinePoolGasHatchSpeed8D.CAP_DRONE_PRIORITY > BuildOrder.EMERGENCY_DEFENSE_PRIORITY);
        assertTrue(NinePoolGasHatchSpeed8D.CAP_DRONE_PRIORITY < new Time(2, 0).getFrames());
    }
}
