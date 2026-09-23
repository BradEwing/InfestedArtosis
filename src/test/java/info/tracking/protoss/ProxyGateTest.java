package info.tracking.protoss;

import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.BaseData;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import org.junit.jupiter.api.Test;
import util.Distance;
import util.Time;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Positions and frames are GAME_LSP4O001's (Destination): our natural's base location, the proxy Probe shown
 * beside it, and the first two Zealots completing there.
 */
class ProxyGateTest {

    private static final TilePosition NATURAL_LOCATION = new TilePosition(63, 19);

    private static final Set<TilePosition> OUR_BASE_TILES =
            Distance.tilesWithinManhattanDistance(NATURAL_LOCATION, BaseData.NATURAL_DEFENSE_TILE_RADIUS);

    private static final Time PROBE_SHOWN = new Time(2481);

    private static final Position PROBE_POSITION = new Position(2035, 738);

    private static final Time FIRST_ZEALOT_COMPLETED = new Time(4063);

    private static final Position FIRST_ZEALOT_POSITION = new Position(2057, 843);

    private static final Time SECOND_ZEALOT_COMPLETED = new Time(4097);

    private static final Position SECOND_ZEALOT_POSITION = new Position(2035, 841);

    private static final Position ENEMY_MAIN_POSITION = new Position(2112, 3824);

    private static final Time AFTER_CUTOFF = new Time(ProxyGate.DETECTION_CUTOFF.getFrames() + 1);

    @Test
    void aProxiedGatewayInsideTheCutoffIsDetected() {
        assertTrue(ProxyGate.matches(proxiedGateways(gateway(ProxyGate.DETECTION_CUTOFF, true)), 0));
    }

    @Test
    void aProxiedGatewayFirstSeenAfterTheCutoffIsNotDetected() {
        assertFalse(ProxyGate.matches(proxiedGateways(gateway(AFTER_CUTOFF, true)), 0));
    }

    @Test
    void aGatewayAwayFromOurBasesIsNotDetected() {
        assertFalse(ProxyGate.matches(proxiedGateways(gateway(PROBE_SHOWN, false)), 0));
    }

    @Test
    void aProxiedProbeAloneIsNotDetected() {
        ObservedUnit probe = ObservedUnitFixture.observedUnit(UnitType.Protoss_Probe, PROBE_SHOWN);
        probe.setProxied(true);
        probe.markCompletedWhileObserved(PROBE_SHOWN, PROBE_POSITION);

        assertEquals(0, proxiedGateways(probe));
        assertEquals(0, localZealots(ProxyGate.DETECTION_CUTOFF, probe));
        assertFalse(ProxyGate.matches(proxiedGateways(probe), localZealots(ProxyGate.DETECTION_CUTOFF, probe)));
    }

    /**
     * BWAPI raises the enemy onUnitComplete when it first shows an already complete unit, so a single Zealot
     * walking onto the natural unseen reports a local completion. One is not enough.
     */
    @Test
    void oneZealotCompletingAtOurNaturalIsNotDetected() {
        ObservedUnit zealot = zealot(FIRST_ZEALOT_COMPLETED, FIRST_ZEALOT_POSITION);

        assertEquals(1, localZealots(ProxyGate.DETECTION_CUTOFF, zealot));
        assertFalse(ProxyGate.matches(0, localZealots(ProxyGate.DETECTION_CUTOFF, zealot)));
    }

    @Test
    void twoZealotsCompletingAtOurNaturalAreDetected() {
        ObservedUnit first = zealot(FIRST_ZEALOT_COMPLETED, FIRST_ZEALOT_POSITION);
        ObservedUnit second = zealot(SECOND_ZEALOT_COMPLETED, SECOND_ZEALOT_POSITION);

        assertEquals(2, localZealots(ProxyGate.DETECTION_CUTOFF, first, second));
        assertTrue(ProxyGate.matches(0, localZealots(ProxyGate.DETECTION_CUTOFF, first, second)));
    }

    /**
     * GAME_LSP4O001: Zealot 176 completes at the natural on frame 4063 and Zealot 179 on 4097. The first alone
     * does not detect; both do, by frame 4097.
     */
    @Test
    void theLsp4o001ZealotsDetectByTheSecondCompletion() {
        ObservedUnit zealot176 = zealot(FIRST_ZEALOT_COMPLETED, FIRST_ZEALOT_POSITION);
        ObservedUnit zealot179 = zealot(SECOND_ZEALOT_COMPLETED, SECOND_ZEALOT_POSITION);

        assertFalse(ProxyGate.matches(0, localZealots(FIRST_ZEALOT_COMPLETED, zealot176, zealot179)));
        assertTrue(ProxyGate.matches(0, localZealots(SECOND_ZEALOT_COMPLETED, zealot176, zealot179)));
    }

    @Test
    void firstSightOfAlreadyCompleteZealotsIsNotALocalCompletion() {
        ObservedUnit first = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, FIRST_ZEALOT_COMPLETED);
        first.markCompleted(FIRST_ZEALOT_COMPLETED);
        ObservedUnit second = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, SECOND_ZEALOT_COMPLETED);
        second.markCompleted(SECOND_ZEALOT_COMPLETED);

        assertEquals(0, localZealots(ProxyGate.DETECTION_CUTOFF, first, second));
    }

    @Test
    void zealotsCompletingAwayFromOurBasesAreNotDetected() {
        ObservedUnit first = zealot(FIRST_ZEALOT_COMPLETED, ENEMY_MAIN_POSITION);
        ObservedUnit second = zealot(SECOND_ZEALOT_COMPLETED, ENEMY_MAIN_POSITION);

        assertEquals(0, localZealots(ProxyGate.DETECTION_CUTOFF, first, second));
    }

    @Test
    void zealotsCompletingAtOurNaturalAfterTheCutoffAreNotCounted() {
        ObservedUnit first = zealot(AFTER_CUTOFF, FIRST_ZEALOT_POSITION);
        ObservedUnit second = zealot(AFTER_CUTOFF, SECOND_ZEALOT_POSITION);

        assertEquals(0, localZealots(ProxyGate.DETECTION_CUTOFF, first, second));
    }

    @Test
    void aZealotCompletionIsCountedAfterTheZealotDies() {
        ObservedUnit zealot = zealot(FIRST_ZEALOT_COMPLETED, FIRST_ZEALOT_POSITION);
        zealot.setDestroyedFrame(new Time(FIRST_ZEALOT_COMPLETED.getFrames() + 100));

        assertEquals(1, localZealots(ProxyGate.DETECTION_CUTOFF, zealot));
    }

    @Test
    void onlyTheFirstCompletionReportIsKept() {
        ObservedUnit zealot = zealot(FIRST_ZEALOT_COMPLETED, FIRST_ZEALOT_POSITION);
        zealot.markCompletedWhileObserved(AFTER_CUTOFF, ENEMY_MAIN_POSITION);

        assertEquals(FIRST_ZEALOT_COMPLETED, zealot.getCompletedWhileObservedFrame());
        assertEquals(FIRST_ZEALOT_POSITION, zealot.getCompletedWhileObservedPosition());
        assertTrue(zealot.isCompleted());
    }

    @Test
    void noEvidenceIsNotDetected() {
        assertFalse(ProxyGate.matches(0, 0));
    }

    @Test
    void theEvidenceNamesTheArmsThatFired() {
        assertEquals("GATEWAY", ProxyGate.evidence(1, 0));
        assertEquals("GATEWAY", ProxyGate.evidence(1, 1));
        assertEquals("ZEALOTS", ProxyGate.evidence(0, 2));
        assertEquals("GATEWAY+ZEALOTS", ProxyGate.evidence(1, 2));
        assertEquals("", ProxyGate.evidence(0, 1));
    }

    @Test
    void theDetectionLabelCarriesTheEvidence() {
        ProxyGate strategy = new ProxyGate();
        assertEquals("ProxyGate", strategy.getDetectionLabel());

        assertTrue(strategy.recordEvidence(0, 2));

        assertEquals("ProxyGate:ZEALOTS", strategy.getDetectionLabel());
        assertEquals("ProxyGate", strategy.getName());
    }

    @Test
    void isAProtossStrategyNamedProxyGate() {
        ProxyGate strategy = new ProxyGate();
        assertEquals("ProxyGate", strategy.getName());
        assertEquals(Race.Protoss, strategy.getRace());
    }

    private static ObservedUnit gateway(Time firstObserved, boolean proxied) {
        ObservedUnit gateway = ObservedUnitFixture.observedUnit(UnitType.Protoss_Gateway, firstObserved);
        gateway.setProxied(proxied);
        return gateway;
    }

    private static ObservedUnit zealot(Time completed, Position position) {
        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, completed);
        zealot.markCompletedWhileObserved(completed, position);
        return zealot;
    }

    private static int proxiedGateways(ObservedUnit observedUnit) {
        return ObservedUnitFixture.trackerHolding(observedUnit)
                .getProxiedCountByTypeBeforeTime(UnitType.Protoss_Gateway, ProxyGate.DETECTION_CUTOFF);
    }

    private static int localZealots(Time by, ObservedUnit... units) {
        return ObservedUnitFixture.countCompletedWhileObservedOnTiles(Arrays.asList(units), UnitType.Protoss_Zealot,
                OUR_BASE_TILES, by);
    }
}
