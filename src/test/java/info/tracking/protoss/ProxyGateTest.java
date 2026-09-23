package info.tracking.protoss;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import info.tracking.ObservedUnitTracker;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Positions and frames are GAME_LSP4O001's (Destination): our scouting sees the enemy Nexus on frame 3314 and
 * no tech before the window closes; the Cybernetics Core in the enemy main is first shown on frame 5035 and
 * the first proxy Gateway on frame 6177. Area membership and our-side ground distance need a live BWEM map, so
 * the AWAY test stands in for them with the enemy main as the one home position.
 */
class ProxyGateTest {

    private static final Position ENEMY_MAIN_POSITION = new Position(2112, 3824);

    private static final Position OUR_NATURAL_POSITION = new Position(2057, 843);

    private static final Time ENEMY_MAIN_REACHED = new Time(3314);

    private static final Time CORE_SHOWN = new Time(5035);

    private static final Position CORE_POSITION = new Position(1840, 3712);

    private static final Time GATEWAY_SHOWN = new Time(6177);

    private static final Position PROXY_GATEWAY_POSITION = new Position(2240, 1424);

    private static final Time AFTER_GATEWAY_CUTOFF = new Time(ProxyGate.GATEWAY_CUTOFF.getFrames() + 1);

    private static final Time AFTER_WINDOW = new Time(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() + 1);

    private static final Time BEFORE_WINDOW = new Time(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() - 1);

    private static final Predicate<Position> AWAY = position -> ProxyGate.isAway(true,
            ENEMY_MAIN_POSITION.equals(position), OUR_NATURAL_POSITION.equals(position));

    @Test
    void aGatewayInTheEnemyMainOrNaturalIsNotAway() {
        assertFalse(ProxyGate.isAway(true, true, false));
    }

    @Test
    void aGatewayOutsideTheEnemyMainAndNaturalIsAway() {
        assertTrue(ProxyGate.isAway(true, false, false));
    }

    @Test
    void aGatewayOnOurSideIsAwayWhereverTheEnemyMainIs() {
        assertTrue(ProxyGate.isAway(true, true, true));
        assertTrue(ProxyGate.isAway(false, false, true));
    }

    @Test
    void aGatewayOffOurSideIsNotAwayWhileTheEnemyMainIsUnknown() {
        assertFalse(ProxyGate.isAway(false, false, false));
    }

    @Test
    void aGatewayInTheEnemyMainIsNotDetected() {
        ObservedUnitTracker tracker = trackerHolding(UnitType.Protoss_Gateway, ENEMY_MAIN_POSITION, ENEMY_MAIN_REACHED);

        assertFalse(ProxyGate.hasGatewayAway(tracker, AWAY));
    }

    @Test
    void aGatewayOnOurSideBeforeTheCutoffIsDetected() {
        ObservedUnitTracker tracker = trackerHolding(UnitType.Protoss_Gateway, OUR_NATURAL_POSITION,
                ProxyGate.GATEWAY_CUTOFF);

        assertTrue(ProxyGate.hasGatewayAway(tracker, AWAY));
    }

    @Test
    void aGatewayOnOurSideFirstSeenAfterTheCutoffIsNotDetected() {
        ObservedUnitTracker tracker = trackerHolding(UnitType.Protoss_Gateway, OUR_NATURAL_POSITION,
                AFTER_GATEWAY_CUTOFF);

        assertFalse(ProxyGate.hasGatewayAway(tracker, AWAY));
    }

    @Test
    void aGatewayWithNoKnownPositionIsNotDetected() {
        ObservedUnitTracker tracker = trackerHolding(UnitType.Protoss_Gateway, null, ENEMY_MAIN_REACHED);

        assertFalse(ProxyGate.hasGatewayAway(tracker, position -> true));
    }

    @Test
    void anotherBuildingOnOurSideIsNotAGatewayAway() {
        ObservedUnitTracker tracker = trackerHolding(UnitType.Protoss_Pylon, OUR_NATURAL_POSITION, ENEMY_MAIN_REACHED);

        assertFalse(ProxyGate.hasGatewayAway(tracker, AWAY));
    }

    @Test
    void aMainNeverReachedIsNotEmpty() {
        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, null, false));
    }

    @Test
    void aMainReachedOnlyAfterTheWindowIsNotEmpty() {
        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, AFTER_WINDOW, false));
    }

    @Test
    void aReachedMainIsNotJudgedBeforeTheWindowCloses() {
        assertFalse(ProxyGate.isMainEmpty(BEFORE_WINDOW, ENEMY_MAIN_REACHED, false));
    }

    @Test
    void aReachedMainWithNoTechByTheWindowEndIsEmpty() {
        assertTrue(ProxyGate.isMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END, ENEMY_MAIN_REACHED, false));
        assertTrue(ProxyGate.isMainEmpty(AFTER_WINDOW, ProxyGate.MAIN_SCOUT_WINDOW_END, false));
    }

    @Test
    void aReachedMainWithAGatewaySeenIsNotEmpty() {
        ObservedUnitTracker tracker = trackerHolding(UnitType.Protoss_Gateway, ENEMY_MAIN_POSITION, ENEMY_MAIN_REACHED);

        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, ENEMY_MAIN_REACHED, techObserved(tracker)));
    }

    @Test
    void aReachedMainWithACoreSeenIsNotEmpty() {
        ObservedUnitTracker tracker = trackerHolding(UnitType.Protoss_Cybernetics_Core, CORE_POSITION,
                ProxyGate.MAIN_SCOUT_WINDOW_END);

        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, ENEMY_MAIN_REACHED, techObserved(tracker)));
    }

    @Test
    void everyTechBuildingBlocksAnEmptyMain() {
        for (UnitType tech : ProxyGate.MAIN_TECH) {
            assertTrue(techObserved(trackerHolding(tech, ENEMY_MAIN_POSITION, ENEMY_MAIN_REACHED)), tech.toString());
        }
        assertEquals(5, ProxyGate.MAIN_TECH.length);
    }

    @Test
    void aPylonDoesNotBlockAnEmptyMain() {
        assertFalse(techObserved(trackerHolding(UnitType.Protoss_Pylon, ENEMY_MAIN_POSITION, ENEMY_MAIN_REACHED)));
    }

    @Test
    void techFirstSeenAfterTheWindowDoesNotBlockAnEmptyMain() {
        assertFalse(techObserved(trackerHolding(UnitType.Protoss_Cybernetics_Core, CORE_POSITION, AFTER_WINDOW)));
    }

    /**
     * GAME_LSP4O001: the Nexus is in vision on frame 3314, and no tech is seen before the window closes; the Core
     * is first shown on frame 5035 and the first Gateway on frame 6177. MAIN_EMPTY fires when the window closes,
     * before the natural dies on frame 4817.
     */
    @Test
    void theLsp4o001ScoutingDetectsAnEmptyMain() {
        ObservedUnitTracker core = trackerHolding(UnitType.Protoss_Cybernetics_Core, CORE_POSITION, CORE_SHOWN);
        ObservedUnitTracker gateway = trackerHolding(UnitType.Protoss_Gateway, PROXY_GATEWAY_POSITION, GATEWAY_SHOWN);

        assertFalse(techObserved(core));
        assertFalse(techObserved(gateway));
        assertFalse(ProxyGate.isMainEmpty(BEFORE_WINDOW, ENEMY_MAIN_REACHED, techObserved(core)));
        assertTrue(ProxyGate.isMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END, ENEMY_MAIN_REACHED, techObserved(core)));
        assertTrue(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() < 4817);

        ProxyGate strategy = new ProxyGate();
        assertTrue(strategy.recordEvidence(false, true));
        assertEquals("ProxyGate:MAIN_EMPTY", strategy.getDetectionLabel());
    }

    /**
     * GAME_LSP4O001: Gateway 169, shown on frame 6177 at (2240,1424), stands outside the enemy main and natural,
     * before the Gateway cutoff, so GATEWAY_AWAY would also have fired had MAIN_EMPTY not.
     */
    @Test
    void theLsp4o001ProxyGatewayIsAway() {
        ObservedUnitTracker gateway = trackerHolding(UnitType.Protoss_Gateway, PROXY_GATEWAY_POSITION, GATEWAY_SHOWN);

        assertTrue(GATEWAY_SHOWN.lessThanOrEqual(ProxyGate.GATEWAY_CUTOFF));
        assertTrue(ProxyGate.hasGatewayAway(gateway, AWAY));
    }

    @Test
    void noEvidenceIsNotDetected() {
        assertFalse(new ProxyGate().recordEvidence(false, false));
    }

    @Test
    void theEvidenceNamesTheArmsThatFired() {
        assertEquals("GATEWAY_AWAY", ProxyGate.evidence(true, false));
        assertEquals("MAIN_EMPTY", ProxyGate.evidence(false, true));
        assertEquals("GATEWAY_AWAY+MAIN_EMPTY", ProxyGate.evidence(true, true));
        assertEquals("", ProxyGate.evidence(false, false));
    }

    @Test
    void theDetectionLabelCarriesTheEvidence() {
        ProxyGate strategy = new ProxyGate();
        assertEquals("ProxyGate", strategy.getDetectionLabel());

        assertTrue(strategy.recordEvidence(true, false));

        assertEquals("ProxyGate:GATEWAY_AWAY", strategy.getDetectionLabel());
        assertEquals("ProxyGate", strategy.getName());
    }

    @Test
    void isAProtossStrategyNamedProxyGate() {
        ProxyGate strategy = new ProxyGate();
        assertEquals("ProxyGate", strategy.getName());
        assertEquals(Race.Protoss, strategy.getRace());
    }

    private static ObservedUnitTracker trackerHolding(UnitType unitType, Position position, Time firstObserved) {
        ObservedUnit observedUnit = ObservedUnitFixture.observedUnit(unitType, position, firstObserved);
        return ObservedUnitFixture.trackerHolding(observedUnit);
    }

    private static boolean techObserved(ObservedUnitTracker tracker) {
        return ProxyGate.isMainTechObserved(tracker);
    }
}
