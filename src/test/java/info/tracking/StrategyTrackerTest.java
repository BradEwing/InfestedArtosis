package info.tracking;

import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.BaseData;
import info.tracking.protoss.ProxyGate;
import info.tracking.protoss.TwoGate;
import info.tracking.zerg.TwoHatchLing;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;
import org.junit.jupiter.api.Test;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;
import util.Distance;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Test
    void proxyGateIsRegisteredAgainstProtossAndUnknownOnly() {
        assertTrue(trackerAgainst(Race.Protoss).isPossibleStrategy("ProxyGate"));
        assertTrue(trackerAgainst(Race.Unknown).isPossibleStrategy("ProxyGate"));
        assertFalse(trackerAgainst(Race.Terran).isPossibleStrategy("ProxyGate"));
        assertFalse(trackerAgainst(Race.Zerg).isPossibleStrategy("ProxyGate"));
    }

    @Test
    void proxyGateIsDroppedOnceTheOpponentResolvesToANonProtossRace() {
        for (Race race : Arrays.asList(Race.Terran, Race.Zerg)) {
            StrategyTracker strategyTracker = trackerAgainst(Race.Unknown);

            strategyTracker.updateRace(race);

            assertFalse(strategyTracker.isPossibleStrategy("ProxyGate"));
        }
        StrategyTracker protoss = trackerAgainst(Race.Unknown);
        protoss.updateRace(Race.Protoss);
        assertTrue(protoss.isPossibleStrategy("ProxyGate"));
    }

    @Test
    void proxyGateImpliesExactlyOneEarlyRush() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);

        strategyTracker.recordDetections(Collections.singleton(new ProxyGate()));
        strategyTracker.recordDetections(Collections.emptySet());

        assertTrue(strategyTracker.isDetectedStrategy("EarlyRush"));
        assertEquals(1, occurrences(strategyTracker, "EarlyRush"));
        assertEquals(1, occurrences(strategyTracker, "ProxyGate"));
    }

    @Test
    void sameFrameProxyGateAndTwoGateResolveToProxyGate() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);

        strategyTracker.recordDetections(new HashSet<>(Arrays.asList(new ProxyGate(), new TwoGate())));

        assertTrue(strategyTracker.isDetectedStrategy("ProxyGate"));
        assertFalse(strategyTracker.isDetectedStrategy("2Gate"));
        assertFalse(strategyTracker.isPossibleStrategy("2Gate"));
        assertEquals(1, occurrences(strategyTracker, "EarlyRush"));
    }

    @Test
    void laterTwoGateEvidenceStaysSuppressedAfterProxyGate() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);
        strategyTracker.recordDetections(Collections.singleton(new ProxyGate()));

        strategyTracker.recordDetections(Collections.singleton(new TwoGate()));

        assertFalse(strategyTracker.isDetectedStrategy("2Gate"));
        assertFalse(strategyTracker.isPossibleStrategy("2Gate"));
        assertTrue(strategyTracker.isAnyDetectedStrategy("2Gate", "ProxyGate"));
    }

    @Test
    void proxyGateRetiresATwoGateDetectedOnAnEarlierFrame() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);
        strategyTracker.recordDetections(Collections.singleton(new TwoGate()));
        assertTrue(strategyTracker.isDetectedStrategy("2Gate"));

        strategyTracker.recordDetections(Collections.singleton(new ProxyGate()));

        assertFalse(strategyTracker.isDetectedStrategy("2Gate"));
        assertTrue(strategyTracker.isDetectedStrategy("ProxyGate"));
        assertEquals(1, occurrences(strategyTracker, "EarlyRush"));
    }

    @Test
    void twoGateAloneIsStillDetected() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);

        strategyTracker.recordDetections(Collections.singleton(new TwoGate()));

        assertTrue(strategyTracker.isDetectedStrategy("2Gate"));
        assertTrue(strategyTracker.isPossibleStrategy("ProxyGate"));
    }

    /**
     * GAME_LSP4O001: the first local Zealot completes at frame 4063 and the third, which is 2Gate's volume
     * evidence, at 4235. ProxyGate, fed the first Zealot's completion, fires on the first frame; 2Gate fires on
     * the later one and is suppressed.
     */
    @Test
    void theLsp4o001ProxyGateCarriesNoTwoGateCoLabel() {
        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, new Time(4063));
        zealot.markCompletedWhileObserved(new Time(4063), new Position(2057, 843));
        Set<TilePosition> naturalTiles = Distance.tilesWithinManhattanDistance(new TilePosition(63, 19),
                BaseData.NATURAL_DEFENSE_TILE_RADIUS);
        assertTrue(ObservedUnitFixture.trackerHolding(zealot)
                .hasCompletedWhileObservedOnTiles(UnitType.Protoss_Zealot, naturalTiles, new Time(4063)));

        StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);
        strategyTracker.recordDetections(Collections.singleton(new ProxyGate()));
        strategyTracker.recordDetections(Collections.singleton(new TwoGate()));

        assertEquals(new HashSet<>(Arrays.asList("ProxyGate", "EarlyRush")),
                new HashSet<>(Arrays.asList(strategyTracker.getDetectedStrategiesAsString().split(";"))));
    }

    @Test
    void eachDetectionIsReportedOnceOnTheFrameItLands() {
        List<String> reported = new ArrayList<>();
        PlanEvents.register(new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
            }

            @Override
            public void onStrategyDetected(String strategyName) {
                reported.add(strategyName);
            }
        });
        try {
            StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);
            strategyTracker.recordDetections(new HashSet<>(Arrays.asList(new ProxyGate(), new TwoGate())));
            strategyTracker.recordDetections(Collections.emptySet());
            strategyTracker.recordDetections(Collections.singleton(new TwoGate()));

            assertEquals(2, reported.size());
            assertEquals(new HashSet<>(Arrays.asList("ProxyGate", "EarlyRush")), new HashSet<>(reported));
        } finally {
            PlanEvents.clear();
        }
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
