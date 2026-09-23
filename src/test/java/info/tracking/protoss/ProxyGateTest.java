package info.tracking.protoss;

import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.BaseData;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import info.tracking.ObservedUnitTracker;
import org.junit.jupiter.api.Test;
import util.Distance;
import util.Time;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Positions and frames are GAME_LSP4O001's (Destination): our natural's base location, the proxy Probe shown
 * beside it, and the first Zealot completing there.
 */
class ProxyGateTest {

    private static final TilePosition NATURAL_LOCATION = new TilePosition(63, 19);

    private static final Set<TilePosition> OUR_BASE_TILES =
            Distance.tilesWithinManhattanDistance(NATURAL_LOCATION, BaseData.NATURAL_DEFENSE_TILE_RADIUS);

    private static final Time PROBE_SHOWN = new Time(2481);

    private static final Position PROBE_POSITION = new Position(2035, 738);

    private static final Time ZEALOT_COMPLETED = new Time(4063);

    private static final Position ZEALOT_POSITION = new Position(2057, 843);

    private static final Position ENEMY_MAIN_POSITION = new Position(2112, 3824);

    private static final Time AFTER_CUTOFF = new Time(ProxyGate.DETECTION_CUTOFF.getFrames() + 1);

    @Test
    void aProxiedGatewayInsideTheCutoffIsDetected() {
        assertTrue(ProxyGate.matches(trackerHolding(gateway(ProxyGate.DETECTION_CUTOFF, true)), OUR_BASE_TILES));
    }

    @Test
    void aProxiedGatewayFirstSeenAfterTheCutoffIsNotDetected() {
        assertFalse(ProxyGate.matches(trackerHolding(gateway(AFTER_CUTOFF, true)), OUR_BASE_TILES));
    }

    @Test
    void aGatewayAwayFromOurBasesIsNotDetected() {
        assertFalse(ProxyGate.matches(trackerHolding(gateway(PROBE_SHOWN, false)), OUR_BASE_TILES));
    }

    @Test
    void aProxiedProbeAloneIsNotDetected() {
        ObservedUnit probe = ObservedUnitFixture.observedUnit(UnitType.Protoss_Probe, PROBE_SHOWN);
        probe.setProxied(true);
        probe.markCompletedWhileObserved(PROBE_SHOWN, PROBE_POSITION);

        assertFalse(ProxyGate.matches(trackerHolding(probe), OUR_BASE_TILES));
    }

    @Test
    void aZealotCompletingAtOurNaturalIsDetected() {
        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, ZEALOT_COMPLETED);
        zealot.markCompletedWhileObserved(ZEALOT_COMPLETED, ZEALOT_POSITION);

        assertTrue(OUR_BASE_TILES.contains(ZEALOT_POSITION.toTilePosition()));
        assertTrue(ProxyGate.matches(trackerHolding(zealot), OUR_BASE_TILES));
    }

    @Test
    void firstSightOfAnAlreadyCompleteZealotIsNotALocalCompletion() {
        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, ZEALOT_COMPLETED);
        zealot.markCompleted(ZEALOT_COMPLETED);

        assertFalse(ProxyGate.matches(trackerHolding(zealot), OUR_BASE_TILES));
    }

    @Test
    void aZealotCompletingAwayFromOurBasesIsNotDetected() {
        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, ZEALOT_COMPLETED);
        zealot.markCompletedWhileObserved(ZEALOT_COMPLETED, ENEMY_MAIN_POSITION);

        assertFalse(ProxyGate.matches(trackerHolding(zealot), OUR_BASE_TILES));
    }

    @Test
    void aZealotCompletingAtOurNaturalAfterTheCutoffIsNotDetected() {
        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, AFTER_CUTOFF);
        zealot.markCompletedWhileObserved(AFTER_CUTOFF, ZEALOT_POSITION);

        assertFalse(ProxyGate.matches(trackerHolding(zealot), OUR_BASE_TILES));
    }

    @Test
    void aZealotCompletionIsKeptAfterTheZealotDies() {
        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, ZEALOT_COMPLETED);
        zealot.markCompletedWhileObserved(ZEALOT_COMPLETED, ZEALOT_POSITION);
        zealot.setDestroyedFrame(new Time(ZEALOT_COMPLETED.getFrames() + 100));

        assertTrue(ProxyGate.matches(trackerHolding(zealot), OUR_BASE_TILES));
    }

    @Test
    void onlyTheFirstCompletionReportIsKept() {
        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, ZEALOT_COMPLETED);
        zealot.markCompletedWhileObserved(ZEALOT_COMPLETED, ZEALOT_POSITION);
        zealot.markCompletedWhileObserved(AFTER_CUTOFF, ENEMY_MAIN_POSITION);

        assertEquals(ZEALOT_COMPLETED, zealot.getCompletedWhileObservedFrame());
        assertEquals(ZEALOT_POSITION, zealot.getCompletedWhileObservedPosition());
        assertTrue(zealot.isCompleted());
    }

    @Test
    void noEvidenceIsNotDetected() {
        assertFalse(ProxyGate.matches(new ObservedUnitTracker(), Collections.emptySet()));
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

    private static ObservedUnitTracker trackerHolding(ObservedUnit observedUnit) {
        return ObservedUnitFixture.trackerHolding(observedUnit);
    }
}
