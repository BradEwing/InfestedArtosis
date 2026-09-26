package info.tracking;

import bwapi.Race;
import info.tracking.protoss.ProxyGate;
import info.tracking.protoss.TwoGate;
import info.tracking.terran.TerranWallMain;
import info.tracking.terran.TerranWallNatural;
import info.tracking.zerg.TwoHatchLing;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;
import org.junit.jupiter.api.Test;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
     * GAME_LSP4O001: the third local Zealot, 2Gate's volume evidence, shows on frame 4235, and ProxyGate's
     * MAIN_EMPTY arm fires when the main scouting window closes on frame 4320. ProxyGate retires the 2Gate
     * detected on the earlier frame, and later volume evidence stays suppressed.
     */
    @Test
    void theLsp4o001ProxyGateCarriesNoTwoGateCoLabel() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);
        strategyTracker.recordDetections(Collections.singleton(new TwoGate()));
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

    @Test
    void theDetectionIsReportedUnderItsDetectionLabel() {
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
            public void onStrategyDetected(String detectionLabel) {
                reported.add(detectionLabel);
            }
        });
        try {
            StrategyTracker strategyTracker = trackerAgainst(Race.Protoss);
            ProxyGate mainEmpty = new ProxyGate() {
                @Override
                public String getDetectionLabel() {
                    return "ProxyGate:MAIN_EMPTY";
                }
            };

            strategyTracker.recordDetections(Collections.singleton(mainEmpty));

            assertEquals(new HashSet<>(Arrays.asList("ProxyGate:MAIN_EMPTY", "EarlyRush")), new HashSet<>(reported));
            assertTrue(strategyTracker.isDetectedStrategy("ProxyGate"));
        } finally {
            PlanEvents.clear();
        }
    }

    @Test
    void theWallDetectorsAreWatchedAgainstTerranAndUnknownOnly() {
        for (Race race : Arrays.asList(Race.Terran, Race.Unknown)) {
            assertTrue(trackerAgainst(race).isPossibleStrategy(TerranWallNatural.NAME), race.toString());
            assertTrue(trackerAgainst(race).isPossibleStrategy(TerranWallMain.NAME), race.toString());
        }
        for (Race race : Arrays.asList(Race.Protoss, Race.Zerg)) {
            assertFalse(trackerAgainst(race).isPossibleStrategy(TerranWallNatural.NAME), race.toString());
            assertFalse(trackerAgainst(race).isPossibleStrategy(TerranWallMain.NAME), race.toString());
        }
    }

    @Test
    void eachWallIsReportedOncePerGame() {
        List<String> reported = new ArrayList<>();
        PlanEvents.register(strategyRecorder(reported));
        try {
            StrategyTracker strategyTracker = trackerAgainst(Race.Terran);
            TerranWallNatural natural = new TerranWallNatural();
            TerranWallMain main = new TerranWallMain();

            strategyTracker.recordDetections(Collections.singleton(natural));
            strategyTracker.recordDetections(Collections.emptySet());
            strategyTracker.recordDetections(Collections.singleton(main));
            strategyTracker.recordDetections(new HashSet<>(Arrays.asList(natural, main)));

            assertEquals(Arrays.asList(TerranWallNatural.NAME, TerranWallMain.NAME), reported);
            assertEquals(1, occurrences(strategyTracker, TerranWallNatural.NAME));
            assertEquals(1, occurrences(strategyTracker, TerranWallMain.NAME));
        } finally {
            PlanEvents.clear();
        }
    }

    @Test
    void aWallDetectedThisGameIsATerranWall() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Terran);
        assertFalse(strategyTracker.isTerranWallDetected());

        strategyTracker.recordDetections(Collections.singleton(new TerranWallMain()));

        assertTrue(strategyTracker.isTerranWallDetected());
    }

    @Test
    void aWallDetectedLastGameIsATerranWall() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Terran);

        strategyTracker.setPreviousGameDetectedStrategies("1Base;TerranWallNatural");

        assertTrue(strategyTracker.isTerranWallDetected());
    }

    @Test
    void theRecordedStrategiesOfAWalledGameReadAsAWallNextGame() {
        StrategyTracker walledGame = trackerAgainst(Race.Terran);
        walledGame.recordDetections(Collections.singleton(new TerranWallMain()));
        StrategyTracker nextGame = trackerAgainst(Race.Terran);

        nextGame.setPreviousGameDetectedStrategies(walledGame.getDetectedStrategiesAsString());

        assertTrue(nextGame.isTerranWallDetected());
    }

    @Test
    void otherStrategiesLastGameAreNotATerranWall() {
        StrategyTracker strategyTracker = trackerAgainst(Race.Terran);

        strategyTracker.setPreviousGameDetectedStrategies("1Base;2RaxAcademy;SCVRush");

        assertFalse(strategyTracker.isTerranWallDetected());
    }

    private static PlanEventSink strategyRecorder(List<String> reported) {
        return new PlanEventSink() {
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
            public void onStrategyDetected(String detectionLabel) {
                reported.add(detectionLabel);
            }
        };
    }

    private static StrategyTracker trackerAgainst(Race race) {
        return new StrategyTracker(null, race, new ObservedUnitTracker(), null, null, null, null);
    }

    private static long occurrences(StrategyTracker strategyTracker, String strategyName) {
        return Arrays.stream(strategyTracker.getDetectedStrategiesAsString().split(";"))
                .filter(strategyName::equals)
                .count();
    }
}
