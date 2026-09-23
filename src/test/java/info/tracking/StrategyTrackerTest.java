package info.tracking;

import bwapi.Race;
import info.tracking.zerg.TwoHatchLing;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyTrackerTest {

    @Test
    void twoHatchLingImpliesEarlyRush() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Zerg);
        strategyTracker.getDetectedStrategies().add(new TwoHatchLing());

        strategyTracker.applyStrategyImplications();

        assertTrue(strategyTracker.isDetectedStrategy("EarlyRush"));
        assertFalse(strategyTracker.isPossibleStrategy("EarlyRush"));
        assertEquals(1, occurrences(strategyTracker, "EarlyRush"));
    }

    @Test
    void implicationDoesNotDuplicateAnAlreadyDetectedEarlyRush() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Zerg);
        strategyTracker.getDetectedStrategies().add(new TwoHatchLing());

        strategyTracker.applyStrategyImplications();
        strategyTracker.applyStrategyImplications();

        assertEquals(1, occurrences(strategyTracker, "EarlyRush"));
    }

    @Test
    void earlyRushIsNotImpliedWithoutAnImplyingStrategy() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Zerg);

        strategyTracker.applyStrategyImplications();

        assertFalse(strategyTracker.isDetectedStrategy("EarlyRush"));
        assertTrue(strategyTracker.isPossibleStrategy("EarlyRush"));
    }

    @Test
    void twoHatchLingIsWatchedAgainstZerg() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Unknown);

        strategyTracker.updateRace(Race.Zerg);

        assertTrue(strategyTracker.isPossibleStrategy("2HatchLing"));
    }

    @Test
    void twoHatchLingIsDroppedForNonZergOpponents() {
        for (Race race : Arrays.asList(Race.Protoss, Race.Terran)) {
            StrategyTracker strategyTracker = trackerAgainst(Race.Unknown);

            strategyTracker.updateRace(race);

            assertFalse(strategyTracker.isPossibleStrategy("2HatchLing"));
        }
    }

    @Test
    void twoHatchLingIsNotRegisteredAgainstAKnownNonZergRace() {
        assertFalse(trackerAgainst(Race.Protoss).isPossibleStrategy("2HatchLing"));
    }

    private static StrategyTracker trackerAgainst(Race race) {
        return new StrategyTracker(null, race, new ObservedUnitTracker(), null, null, null);
    }

    private static long occurrences(StrategyTracker strategyTracker, String strategyName) {
        return Arrays.stream(strategyTracker.getDetectedStrategiesAsString().split(";"))
                .filter(strategyName::equals)
                .count();
    }
}
