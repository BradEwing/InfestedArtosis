package info.tracking.protoss;

import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.ScoutData;
import info.map.BaseArea;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import info.tracking.ObservedUnitTracker;
import org.junit.jupiter.api.Test;
import util.Distance;
import util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BWEM Areas and ground paths need a live map, so the enemy's home stands in as the tiles within
 * {@link #MAIN_TILE_RADIUS} of the main depot plus the natural's wall ground, which is a real BaseArea around
 * the natural depot and one chokepoint. Our side is the tiles within {@link #MAIN_TILE_RADIUS} of our main.
 * The LSP4O001 frames and positions are GAME_LSP4O001's (Destination).
 */
class ProxyGateTest {

    private static final int MAIN_TILE_RADIUS = 12;

    private static final TilePosition ENEMY_MAIN_DEPOT = new TilePosition(100, 100);

    private static final TilePosition ENEMY_NATURAL_DEPOT = new TilePosition(100, 76);

    private static final TilePosition ENEMY_NATURAL_CHOKE = new TilePosition(100, 66);

    private static final TilePosition OUR_MAIN_DEPOT = new TilePosition(20, 20);

    private static final BaseArea ENEMY_NATURAL_WALL = new BaseArea(ENEMY_NATURAL_DEPOT, null,
            Collections.singletonList(ENEMY_NATURAL_CHOKE), ProxyGate.NATURAL_WALL_TILE_RADIUS,
            ProxyGate.NATURAL_WALL_TILE_RADIUS, tile -> null);

    private static final Predicate<TilePosition> AT_ENEMY_HOME = tile ->
            Distance.manhattanTileDistance(tile, ENEMY_MAIN_DEPOT) <= MAIN_TILE_RADIUS
                    || ENEMY_NATURAL_WALL.contains(tile);

    private static final Predicate<Position> AWAY = position -> ProxyGate.isAway(true,
            AT_ENEMY_HOME.test(position.toTilePosition()),
            Distance.manhattanTileDistance(position.toTilePosition(), OUR_MAIN_DEPOT) <= MAIN_TILE_RADIUS);

    private static final Position ENEMY_MAIN_GATEWAY = tileCentre(new TilePosition(103, 98));

    private static final Position NATURAL_WALL_GATEWAY = tileCentre(new TilePosition(103, 64));

    private static final Position THIRD_BASE_GATEWAY = tileCentre(new TilePosition(60, 60));

    private static final Position OUR_SIDE_GATEWAY = tileCentre(new TilePosition(24, 26));

    private static final Position DISTANT_FORGE = tileCentre(new TilePosition(60, 60));

    private static final Time EARLY = new Time(3314);

    private static final Time AFTER_GATEWAY_CUTOFF = new Time(ProxyGate.GATEWAY_CUTOFF.getFrames() + 1);

    private static final Time AFTER_WINDOW = new Time(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() + 1);

    private static final Time BEFORE_WINDOW = new Time(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() - 1);

    private static final int MAIN_BUILDABLE_TILES = 400;

    private static final Time LSP4O001_CORE_SHOWN = new Time(5035);

    private static final Time LSP4O001_GATEWAY_SHOWN = new Time(6177);

    private static final Position LSP4O001_PROXY_GATEWAY = new Position(2240, 1424);

    @Test
    void aGatewayAtTheEnemyHomeIsNotAway() {
        assertFalse(ProxyGate.isAway(true, true, false));
    }

    @Test
    void aGatewayAwayFromTheEnemyHomeIsAway() {
        assertTrue(ProxyGate.isAway(true, false, false));
    }

    @Test
    void aGatewayOnOurSideIsAwayWhetherOrNotTheEnemyHomeIsKnown() {
        assertTrue(ProxyGate.isAway(true, true, true));
        assertTrue(ProxyGate.isAway(false, false, true));
    }

    @Test
    void aGatewayOffOurSideIsNotAwayWhileTheEnemyHomeIsUnknown() {
        assertFalse(ProxyGate.isAway(false, false, false));
    }

    @Test
    void aGatewayInTheEnemyMainIsNotDetected() {
        assertFalse(ProxyGate.hasGatewayAway(gatewayTracker(ENEMY_MAIN_GATEWAY, EARLY), AWAY));
    }

    @Test
    void aWallGatewayAtTheEnemyNaturalChokeIsNotDetected() {
        assertTrue(Distance.manhattanTileDistance(NATURAL_WALL_GATEWAY.toTilePosition(), ENEMY_NATURAL_CHOKE)
                <= ProxyGate.NATURAL_WALL_TILE_RADIUS);
        assertTrue(Distance.manhattanTileDistance(NATURAL_WALL_GATEWAY.toTilePosition(), ENEMY_NATURAL_DEPOT)
                > ProxyGate.NATURAL_WALL_TILE_RADIUS);

        assertFalse(ProxyGate.hasGatewayAway(gatewayTracker(NATURAL_WALL_GATEWAY, EARLY), AWAY));
    }

    @Test
    void aGatewayAtAFarThirdBaseIsDetected() {
        assertTrue(ProxyGate.hasGatewayAway(gatewayTracker(THIRD_BASE_GATEWAY, EARLY), AWAY));
    }

    @Test
    void aGatewayOnOurSideBeforeTheCutoffIsDetected() {
        assertTrue(ProxyGate.hasGatewayAway(gatewayTracker(OUR_SIDE_GATEWAY, ProxyGate.GATEWAY_CUTOFF), AWAY));
    }

    @Test
    void aGatewayOnOurSideFirstSeenAfterTheCutoffIsNotDetected() {
        assertFalse(ProxyGate.hasGatewayAway(gatewayTracker(OUR_SIDE_GATEWAY, AFTER_GATEWAY_CUTOFF), AWAY));
    }

    @Test
    void aGatewayWithNoKnownPositionIsNotDetected() {
        assertFalse(ProxyGate.hasGatewayAway(gatewayTracker(null, EARLY), position -> true));
    }

    @Test
    void anotherBuildingOnOurSideIsNotAGatewayAway() {
        assertFalse(ProxyGate.hasGatewayAway(trackerHolding(UnitType.Protoss_Pylon, OUR_SIDE_GATEWAY, EARLY), AWAY));
    }

    @Test
    void aMainNeverScoutedIsNotEmpty() {
        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, null, false));
    }

    @Test
    void aMainScoutedOnlyAfterTheWindowIsNotEmpty() {
        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, AFTER_WINDOW, false));
    }

    @Test
    void aScoutedMainIsNotJudgedBeforeTheWindowCloses() {
        assertFalse(ProxyGate.isMainEmpty(BEFORE_WINDOW, EARLY, false));
    }

    @Test
    void aMainWhoseDepotAloneWasSeenIsNotDetected() {
        ScoutData scoutData = new ScoutData();
        scoutData.recordEnemyMainVision(null, tiles(12), MAIN_BUILDABLE_TILES, EARLY);

        assertNull(scoutData.getEnemyMainScoutedFrame(null));
        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, scoutData.getEnemyMainScoutedFrame(null), false));
    }

    @Test
    void aWellCoveredMainWithNoTechIsDetected() {
        ScoutData scoutData = new ScoutData();
        scoutData.recordEnemyMainVision(null, tiles(MAIN_BUILDABLE_TILES * 3 / 4), MAIN_BUILDABLE_TILES, EARLY);

        assertEquals(EARLY, scoutData.getEnemyMainScoutedFrame(null));
        assertTrue(ProxyGate.isMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END, scoutData.getEnemyMainScoutedFrame(null),
                false));
    }

    @Test
    void aScoutedMainWithAGatewaySeenIsNotEmpty() {
        boolean tech = homeTech(gatewayTracker(ENEMY_MAIN_GATEWAY, EARLY));

        assertTrue(tech);
        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, EARLY, tech));
    }

    @Test
    void aScoutedMainWithACoreSeenIsNotEmpty() {
        boolean tech = homeTech(trackerHolding(UnitType.Protoss_Cybernetics_Core, ENEMY_MAIN_GATEWAY,
                ProxyGate.MAIN_SCOUT_WINDOW_END));

        assertTrue(tech);
        assertFalse(ProxyGate.isMainEmpty(AFTER_WINDOW, EARLY, tech));
    }

    @Test
    void everyTechBuildingInTheMainBlocksAnEmptyMain() {
        for (UnitType tech : ProxyGate.MAIN_TECH) {
            assertTrue(homeTech(trackerHolding(tech, ENEMY_MAIN_GATEWAY, EARLY)), tech.toString());
        }
        assertEquals(5, ProxyGate.MAIN_TECH.length);
    }

    @Test
    void aForgeWallingTheEnemyNaturalBlocksAnEmptyMain() {
        assertTrue(homeTech(trackerHolding(UnitType.Protoss_Forge, NATURAL_WALL_GATEWAY, EARLY)));
    }

    @Test
    void techAwayFromTheEnemyHomeDoesNotBlockAnEmptyMain() {
        assertFalse(homeTech(trackerHolding(UnitType.Protoss_Forge, DISTANT_FORGE, EARLY)));
        assertFalse(homeTech(gatewayTracker(OUR_SIDE_GATEWAY, EARLY)));
    }

    @Test
    void aPylonDoesNotBlockAnEmptyMain() {
        assertFalse(homeTech(trackerHolding(UnitType.Protoss_Pylon, ENEMY_MAIN_GATEWAY, EARLY)));
    }

    @Test
    void techFirstSeenAfterTheWindowDoesNotBlockAnEmptyMain() {
        assertFalse(homeTech(trackerHolding(UnitType.Protoss_Cybernetics_Core, ENEMY_MAIN_GATEWAY, AFTER_WINDOW)));
    }

    /**
     * GAME_LSP4O001: the Nexus is in vision on frame 3314 and no tech is seen before the window closes; the Core
     * is first shown on frame 5035. The logs cannot tell how much of the main our vision covered, so this case
     * takes the main as covered by 3314: MAIN_EMPTY then fires when the window closes, before the natural dies
     * on frame 4817.
     */
    @Test
    void theLsp4o001MainDetectsAsEmptyOnceCovered() {
        boolean tech = homeTech(trackerHolding(UnitType.Protoss_Cybernetics_Core, ENEMY_MAIN_GATEWAY,
                LSP4O001_CORE_SHOWN));

        assertFalse(tech);
        assertTrue(ProxyGate.isMainEmpty(ProxyGate.MAIN_SCOUT_WINDOW_END, EARLY, tech));
        assertTrue(ProxyGate.MAIN_SCOUT_WINDOW_END.getFrames() < 4817);
    }

    /**
     * GAME_LSP4O001: proxy Gateway 169 is first shown on frame 6177 at (2240,1424), inside the Gateway cutoff and
     * 79 manhattan tiles from the enemy main at (2112,3824), so GATEWAY_AWAY fires whatever the main's coverage.
     */
    @Test
    void theLsp4o001ProxyGatewayIsAway() {
        TilePosition enemyMain = new Position(2112, 3824).toTilePosition();
        TilePosition proxy = LSP4O001_PROXY_GATEWAY.toTilePosition();
        Predicate<Position> away = position -> ProxyGate.isAway(true,
                Distance.manhattanTileDistance(position.toTilePosition(), enemyMain) <= MAIN_TILE_RADIUS, false);

        assertTrue(Distance.manhattanTileDistance(proxy, enemyMain) > MAIN_TILE_RADIUS
                + ProxyGate.NATURAL_WALL_TILE_RADIUS);
        assertTrue(LSP4O001_GATEWAY_SHOWN.lessThanOrEqual(ProxyGate.GATEWAY_CUTOFF));
        assertTrue(ProxyGate.hasGatewayAway(gatewayTracker(LSP4O001_PROXY_GATEWAY, LSP4O001_GATEWAY_SHOWN), away));
    }

    @Test
    void theNaturalWallRadiusMatchesFfe() {
        assertEquals(FFE.PROXIMITY_TILE_RADIUS, ProxyGate.NATURAL_WALL_TILE_RADIUS);
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

    private static Position tileCentre(TilePosition tile) {
        return new Position(tile.getX() * 32 + 16, tile.getY() * 32 + 16);
    }

    private static List<TilePosition> tiles(int count) {
        List<TilePosition> tiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tiles.add(new TilePosition(i % 64, i / 64));
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

    private static boolean homeTech(ObservedUnitTracker tracker) {
        return ProxyGate.isHomeTechObserved(tracker, AT_ENEMY_HOME);
    }
}
