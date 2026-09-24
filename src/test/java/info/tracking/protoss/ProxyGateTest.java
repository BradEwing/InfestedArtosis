package info.tracking.protoss;

import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.ScoutData;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import info.tracking.ObservedUnitTracker;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground paths need a live map, so our side of the map stands in as the positions nearer our main than the enemy
 * main by air. Frames and positions named after a game are that game's, from its unit_events.csv: GAME_LSP4O001
 * (Destination, the proxy 9/9 Gateway) and the batch 20260923-121254 false positives.
 */
class ProxyGateTest {

    private static final Position LSP4O001_OUR_MAIN = new Position(1056, 272);

    private static final Position LSP4O001_ENEMY_MAIN = new Position(2112, 3824);

    private static final Predicate<Position> LSP4O001_OUR_SIDE = onOurSide(LSP4O001_OUR_MAIN, LSP4O001_ENEMY_MAIN);

    private static final Position LSP4O001_PROXY_GATEWAY = new Position(2240, 1424);

    private static final Time LSP4O001_GATEWAY_SHOWN = new Time(6177);

    private static final Position LSP4O001_ZEALOT_AT_OUR_NATURAL = new Position(2035, 841);

    private static final Position LSP4O001_ENEMY_MAIN_CORE = new Position(1840, 3712);

    private static final Time LSP4O001_CORE_SHOWN = new Time(5035);

    private static final Time LSP4O001_NEXUS_SHOWN = new Time(3314);

    private static final Position I1_OUR_MAIN = new Position(288, 3824);

    private static final Position I1_ENEMY_MAIN = new Position(3808, 272);

    private static final Position I1_FORWARD_GATEWAY = new Position(3680, 1104);

    private static final Time I1_FORWARD_GATEWAY_SHOWN = new Time(5059);

    private static final Position EF_HOME_GATEWAY = new Position(1408, 272);

    private static final Time EF_HOME_GATEWAY_SHOWN = new Time(3286);

    private static final Time EF_HOME_GATEWAY_DESTROYED = new Time(7243);

    private static final Position EF_ZEALOT_AT_ENEMY_HOME = new Position(1368, 320);

    private static final Position EF_OUR_MAIN = new Position(2112, 3824);

    private static final Position EF_ENEMY_MAIN = new Position(1056, 272);

    private static final Time EARLY = new Time(3314);

    private static final Time AFTER_GATEWAY_CUTOFF = new Time(ProxyGate.GATEWAY_CUTOFF.getFrames() + 1);

    private static final Time AFTER_WINDOW = new Time(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() + 1);

    private static final Time BEFORE_WINDOW = new Time(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() - 1);

    @Test
    void aGatewayOnOurSideBeforeTheCutoffIsDetected() {
        assertTrue(ProxyGate.hasGatewayAway(ProxyGate.GATEWAY_CUTOFF,
                gatewayTracker(LSP4O001_PROXY_GATEWAY, ProxyGate.GATEWAY_CUTOFF), LSP4O001_OUR_SIDE));
    }

    @Test
    void aGatewayInTheEnemyMainIsNotDetected() {
        assertFalse(ProxyGate.hasGatewayAway(EARLY, gatewayTracker(LSP4O001_ENEMY_MAIN_CORE, EARLY),
                LSP4O001_OUR_SIDE));
    }

    /**
     * GAME_LU01I0I1: Stardust's forward Gateway 842 px from its own depot, with FFE dragoons behind it, fired
     * GATEWAY_AWAY when any Gateway outside the enemy main and natural counted.
     */
    @Test
    void aForwardGatewayOnTheEnemysSideIsNotDetected() {
        assertFalse(ProxyGate.hasGatewayAway(I1_FORWARD_GATEWAY_SHOWN,
                gatewayTracker(I1_FORWARD_GATEWAY, I1_FORWARD_GATEWAY_SHOWN), onOurSide(I1_OUR_MAIN, I1_ENEMY_MAIN)));
    }

    @Test
    void aGatewayOnOurSideFirstSeenAfterTheCutoffIsNotDetected() {
        assertFalse(ProxyGate.hasGatewayAway(AFTER_GATEWAY_CUTOFF,
                gatewayTracker(LSP4O001_PROXY_GATEWAY, AFTER_GATEWAY_CUTOFF), LSP4O001_OUR_SIDE));
    }

    @Test
    void aGatewayOnOurSideNeverFiresAfterTheCutoff() {
        assertFalse(ProxyGate.hasGatewayAway(AFTER_GATEWAY_CUTOFF,
                gatewayTracker(LSP4O001_PROXY_GATEWAY, EARLY), LSP4O001_OUR_SIDE));
    }

    @Test
    void aGatewayWithNoKnownPositionIsNotDetected() {
        assertFalse(ProxyGate.hasGatewayAway(EARLY, gatewayTracker(null, EARLY), position -> true));
    }

    @Test
    void anotherBuildingOnOurSideIsNotAGatewayAway() {
        assertFalse(ProxyGate.hasGatewayAway(EARLY,
                trackerHolding(UnitType.Protoss_Pylon, LSP4O001_PROXY_GATEWAY, EARLY), LSP4O001_OUR_SIDE));
    }

    /**
     * GAME_LSP4O001: proxy Gateway 169 is first shown on frame 6177 at (2240,1424), inside the cutoff and on our
     * half of Destination.
     */
    @Test
    void theLsp4o001ProxyGatewayIsOnOurSide() {
        assertTrue(ProxyGate.hasGatewayAway(LSP4O001_GATEWAY_SHOWN,
                gatewayTracker(LSP4O001_PROXY_GATEWAY, LSP4O001_GATEWAY_SHOWN), LSP4O001_OUR_SIDE));
    }

    @Test
    void theMainIsNotJudgedBeforeTheWindowCloses() {
        ProxyGate strategy = new ProxyGate();

        assertFalse(strategy.decideMainEmpty(BEFORE_WINDOW, () -> {
            throw new AssertionError("judged before the window closed");
        }));
        assertTrue(strategy.decideMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END, () -> true));
    }

    @Test
    void theMainIsJudgedOnceOnTheFirstFrameAtTheWindowEnd() {
        ProxyGate strategy = new ProxyGate();

        assertFalse(strategy.decideMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END, () -> false));

        assertFalse(strategy.decideMainEmpty(AFTER_WINDOW, () -> true));
    }

    /**
     * GAME_LU01I0EF, 0EG, 0EM and 0KP: MAIN_EMPTY fired on the frame the last home tech seen before the window
     * died (8415, 10746, 10525, 8098), because a destroyed unit loses its position. The verdict is now taken
     * once at the window end, so a later frame never fires whatever it sees.
     */
    @Test
    void anEmptyMainNeverFiresAfterTheWindow() {
        ProxyGate strategy = new ProxyGate();
        strategy.decideMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END, () -> false);

        assertFalse(strategy.decideMainEmpty(new Time(8415), () -> true));
    }

    @Test
    void aMainFirstJudgedLateIsJudgedAtMostOnce() {
        ProxyGate strategy = new ProxyGate();

        assertTrue(strategy.decideMainEmpty(AFTER_WINDOW, () -> true));
        assertFalse(strategy.decideMainEmpty(new Time(AFTER_WINDOW.getFrames() + 1), () -> true));
    }

    @Test
    void aHomeGatewayDestroyedAfterTheWindowStillVetoes() {
        ObservedUnit gateway = ObservedUnitFixture.observedUnit(UnitType.Protoss_Gateway, EF_HOME_GATEWAY,
                EF_HOME_GATEWAY_SHOWN);
        gateway.setDestroyedFrame(EF_HOME_GATEWAY_DESTROYED);
        gateway.setLastKnownLocation(null);

        assertTrue(ProxyGate.isTechObserved(ObservedUnitFixture.trackerHolding(gateway)));
    }

    /**
     * GAME_LU01I00J and LU01I0JE: an FFE's Forge seen by the window end vetoes, wherever it stands.
     */
    @Test
    void aForgeVetoesAnEmptyMain() {
        assertTrue(ProxyGate.isTechObserved(trackerHolding(UnitType.Protoss_Forge, null, EARLY)));
    }

    @Test
    void everyTechBuildingVetoesAnEmptyMain() {
        for (UnitType tech : ProxyGate.MAIN_TECH) {
            assertTrue(ProxyGate.isTechObserved(trackerHolding(tech, EF_HOME_GATEWAY, EARLY)), tech.toString());
        }
        assertEquals(5, ProxyGate.MAIN_TECH.length);
    }

    @Test
    void techSeenOnTheLastFrameOfTheWindowVetoes() {
        assertTrue(ProxyGate.isTechObserved(trackerHolding(UnitType.Protoss_Cybernetics_Core, EF_HOME_GATEWAY,
                ProxyGate.MAIN_SCOUT_WINDOW_END)));
    }

    @Test
    void aPylonDoesNotVetoAnEmptyMain() {
        assertFalse(ProxyGate.isTechObserved(trackerHolding(UnitType.Protoss_Pylon, EF_HOME_GATEWAY, EARLY)));
    }

    @Test
    void techFirstSeenAfterTheWindowDoesNotVetoAnEmptyMain() {
        assertFalse(ProxyGate.isTechObserved(trackerHolding(UnitType.Protoss_Cybernetics_Core,
                LSP4O001_ENEMY_MAIN_CORE, LSP4O001_CORE_SHOWN)));
    }

    @Test
    void aMainNeverScoutedIsNotEmpty() {
        assertFalse(ProxyGate.isMainEmpty(null, false, true));
    }

    @Test
    void aMainScoutedOnlyAfterTheWindowIsNotEmpty() {
        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, false, true));
    }

    @Test
    void aScoutedMainWithTechSeenIsNotEmpty() {
        assertFalse(ProxyGate.isMainEmpty(EARLY, true, true));
    }

    /**
     * GAME_LU01I0E8, 0EX, 0L3, 0IG, 00J and 0JE fired at exactly the window end with a covered main and no tech
     * seen; none had a Zealot on our side of the map by then.
     */
    @Test
    void aScoutedMainWithNoTechAndNoZealotOnOurSideIsNotEmpty() {
        assertFalse(ProxyGate.isMainEmpty(EARLY, false, false));
    }

    @Test
    void aScoutedMainWithNoTechAndAZealotOnOurSideIsEmpty() {
        assertTrue(ProxyGate.isMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END, false, true));
    }

    @Test
    void aMainCoveredWithoutItsGatewaySitesIsNotEmpty() {
        ScoutData scoutData = new ScoutData();
        List<TilePosition> gatewaySites = tilesInRow(300, 400);

        scoutData.recordEnemyMainVision(null, tilesInRow(0, 300), 400, gatewaySites, EARLY);

        assertNull(scoutData.getEnemyMainScoutedFrame(null));
        assertFalse(ProxyGate.isMainEmpty(scoutData.getEnemyMainScoutedFrame(null), false, true));
    }

    @Test
    void aMainCoveredWithItsGatewaySitesCanBeEmpty() {
        ScoutData scoutData = new ScoutData();
        List<TilePosition> gatewaySites = tilesInRow(300, 400);

        scoutData.recordEnemyMainVision(null, tilesInRow(100, 400), 400, gatewaySites, EARLY);

        assertEquals(EARLY, scoutData.getEnemyMainScoutedFrame(null));
        assertTrue(ProxyGate.isMainEmpty(scoutData.getEnemyMainScoutedFrame(null), false, true));
    }

    @Test
    void aZealotAtOurNaturalIsOnOurSide() {
        assertTrue(ProxyGate.hasZealotOnOurSide(Collections.singleton(LSP4O001_ZEALOT_AT_OUR_NATURAL),
                LSP4O001_OUR_SIDE));
    }

    @Test
    void aZealotAtTheEnemyHomeIsNotOnOurSide() {
        assertFalse(ProxyGate.hasZealotOnOurSide(Collections.singleton(EF_ZEALOT_AT_ENEMY_HOME),
                onOurSide(EF_OUR_MAIN, EF_ENEMY_MAIN)));
        assertFalse(ProxyGate.hasZealotOnOurSide(Collections.emptySet(), position -> true));
    }

    /**
     * GAME_LSP4O001: the Nexus is in vision on frame 3314, no tech is seen before the window closes (the Core is
     * first shown on frame 5035), and Zealots 176, 179 and 180 stand at our natural by frame 4235. The logs
     * cannot tell how much of the main our vision covered, so this case takes the main as covered by 3314:
     * MAIN_EMPTY then fires when the window closes, before the natural dies on frame 4817.
     */
    @Test
    void theLsp4o001MainDetectsAsEmptyAtTheWindowEnd() {
        boolean tech = ProxyGate.isTechObserved(trackerHolding(UnitType.Protoss_Cybernetics_Core,
                LSP4O001_ENEMY_MAIN_CORE, LSP4O001_CORE_SHOWN));
        boolean zealot = ProxyGate.hasZealotOnOurSide(Arrays.asList(LSP4O001_ZEALOT_AT_OUR_NATURAL,
                new Position(2057, 843)), LSP4O001_OUR_SIDE);
        ProxyGate strategy = new ProxyGate();

        assertTrue(strategy.decideMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END,
                () -> ProxyGate.isMainEmpty(LSP4O001_NEXUS_SHOWN, tech, zealot)));
        assertTrue(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() < 4817);
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

        assertTrue(strategy.recordEvidence(false, true));

        assertEquals("ProxyGate:MAIN_EMPTY", strategy.getDetectionLabel());
        assertEquals("ProxyGate", strategy.getName());
    }

    @Test
    void isAProtossStrategyNamedProxyGate() {
        ProxyGate strategy = new ProxyGate();
        assertEquals("ProxyGate", strategy.getName());
        assertEquals(Race.Protoss, strategy.getRace());
    }

    private static Predicate<Position> onOurSide(Position ourMain, Position enemyMain) {
        return position -> position.getDistance(ourMain) < position.getDistance(enemyMain);
    }

    private static List<TilePosition> tilesInRow(int fromX, int toX) {
        List<TilePosition> tiles = new ArrayList<>();
        for (int x = fromX; x < toX; x++) {
            tiles.add(new TilePosition(x, 0));
        }
        return tiles;
    }

    private static ObservedUnitTracker gatewayTracker(Position position, Time firstObserved) {
        return trackerHolding(UnitType.Protoss_Gateway, position, firstObserved);
    }

    private static ObservedUnitTracker trackerHolding(UnitType unitType, Position position, Time firstObserved) {
        ObservedUnit observedUnit = ObservedUnitFixture.observedUnit(unitType, position, firstObserved);
        return ObservedUnitFixture.trackerHolding(observedUnit);
    }
}
