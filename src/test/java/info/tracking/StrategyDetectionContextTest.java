package info.tracking;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.EnemyMainEvidence;
import info.LV28400NFixture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the main-depot exclusion behind enemyHasExtraDepotInMainArea(). A depot's tile is the tile of its
 * reported centre position, and a 4x3 Hatchery at location (x, y) reports its centre at pixel
 * (32x + 64, 32y + 48), which lies in tile (x + 2, y + 1).
 */
class StrategyDetectionContextTest {

    private static final TilePosition GAME_LSG2U0B4_MAIN_LOCATION = new TilePosition(7, 6);
    private static final Position GAME_LSG2U0B4_MAIN_HATCHERY = new Position(288, 240);
    private static final TilePosition FIGHTING_SPIRIT_MAIN_LOCATION = new TilePosition(117, 7);
    private static final Position FIGHTING_SPIRIT_MAIN_HATCHERY = new Position(3808, 272);
    private static final Position FIGHTING_SPIRIT_STACKED_HATCHERY = new Position(3776, 368);

    /**
     * Location (7,6) puts the Hatchery centre at (7*32 + 64, 6*32 + 48) = (288,240), tile (9,7): inside the
     * 4x3 footprint spanning tiles (7..10, 6..8).
     */
    @Test
    void hatcheryAt288x240IsTheDepotOnLocation7x6() {
        assertTrue(StrategyDetectionContext.occupiesBaseLocation(
                GAME_LSG2U0B4_MAIN_HATCHERY.toTilePosition(), GAME_LSG2U0B4_MAIN_LOCATION));
    }

    /**
     * BWEM builds Base.getCenter() from the 3x3 Command Center footprint, (7*32 + 48, 6*32 + 48) = (272,240),
     * tile (8,7), which is not the Hatchery's reported tile (9,7). An equality test against it never excludes the
     * main depot.
     */
    @Test
    void commandCenterCentreTileIsNotTheHatcheryTile() {
        TilePosition commandCenterCentreTile = new Position(272, 240).toTilePosition();

        assertNotEquals(GAME_LSG2U0B4_MAIN_HATCHERY.toTilePosition(), commandCenterCentreTile);
        assertTrue(StrategyDetectionContext.occupiesBaseLocation(commandCenterCentreTile, GAME_LSG2U0B4_MAIN_LOCATION));
    }

    /**
     * Location (117,7) puts the Hatchery centre at (117*32 + 64, 7*32 + 48) = (3808,272), tile (119,8): inside the
     * footprint spanning tiles (117..120, 7..9).
     */
    @Test
    void fightingSpiritMainHatcheryAt3808x272IsTheDepotOnLocation117x7() {
        assertEquals(new TilePosition(119, 8), FIGHTING_SPIRIT_MAIN_HATCHERY.toTilePosition());
        assertTrue(StrategyDetectionContext.occupiesBaseLocation(
                FIGHTING_SPIRIT_MAIN_HATCHERY.toTilePosition(), FIGHTING_SPIRIT_MAIN_LOCATION));
    }

    /**
     * The stacked second Hatchery at (3776,368) is tile (118,11): row 11 is below the main footprint's rows 7..9,
     * so it counts as an extra depot.
     */
    @Test
    void stackedSecondHatcheryAt3776x368IsNotTheMainDepot() {
        assertEquals(new TilePosition(118, 11), FIGHTING_SPIRIT_STACKED_HATCHERY.toTilePosition());
        assertFalse(StrategyDetectionContext.occupiesBaseLocation(
                FIGHTING_SPIRIT_STACKED_HATCHERY.toTilePosition(), FIGHTING_SPIRIT_MAIN_LOCATION));
    }

    /**
     * The footprint of location (7,6) spans tiles (7..10, 6..8); the tiles just outside each edge are excluded.
     */
    @Test
    void tilesJustOutsideTheFootprintAreNotTheDepot() {
        assertFalse(StrategyDetectionContext.occupiesBaseLocation(new TilePosition(6, 7), GAME_LSG2U0B4_MAIN_LOCATION));
        assertFalse(StrategyDetectionContext.occupiesBaseLocation(new TilePosition(11, 7), GAME_LSG2U0B4_MAIN_LOCATION));
        assertFalse(StrategyDetectionContext.occupiesBaseLocation(new TilePosition(9, 5), GAME_LSG2U0B4_MAIN_LOCATION));
        assertFalse(StrategyDetectionContext.occupiesBaseLocation(new TilePosition(9, 9), GAME_LSG2U0B4_MAIN_LOCATION));
        assertTrue(StrategyDetectionContext.occupiesBaseLocation(new TilePosition(10, 8), GAME_LSG2U0B4_MAIN_LOCATION));
    }

    @Test
    void aPositionNearerOurMainIsOnOurSide() {
        assertTrue(StrategyDetectionContext.isCloserToOurMain(1000, Collections.singletonList(3000)));
    }

    @Test
    void aPositionNearerTheEnemyMainIsNotOnOurSide() {
        assertFalse(StrategyDetectionContext.isCloserToOurMain(3000, Collections.singletonList(1000)));
    }

    @Test
    void anEquidistantPositionIsNotOnOurSide() {
        assertFalse(StrategyDetectionContext.isCloserToOurMain(2000, Collections.singletonList(2000)));
    }

    @Test
    void anUnknownEnemyMainNeedsEveryOtherStartingLocationFarther() {
        assertTrue(StrategyDetectionContext.isCloserToOurMain(1000, Arrays.asList(3000, 2500)));
        assertFalse(StrategyDetectionContext.isCloserToOurMain(1000, Arrays.asList(3000, 800)));
    }

    @Test
    void aPositionWithNoGroundPathToOurMainIsNotOnOurSide() {
        assertFalse(StrategyDetectionContext.isCloserToOurMain(-1, Collections.singletonList(3000)));
    }

    @Test
    void anEnemyMainWithNoGroundPathDoesNotCountAgainstOurSide() {
        assertTrue(StrategyDetectionContext.isCloserToOurMain(1000, Collections.singletonList(-1)));
    }

    /**
     * Game LV28400N's proxy Gateway is 901 px from the empty start, 1664 px from our main and 3360 px from the real
     * main, by air: ground paths need a live map, so air distance stands in for them.
     */
    @Test
    void theLv28400nProxyGatewayIsOnOurSideOnceTheRealMainIsAssigned() {
        LV28400NFixture icarus = new LV28400NFixture();
        icarus.baseData.assignEnemyMain(icarus.realMain, EnemyMainEvidence.DEPOT, UnitType.Protoss_Nexus,
                LV28400NFixture.REAL_NEXUS);

        assertTrue(isOnOurSide(icarus, LV28400NFixture.PROXY_GATEWAY));
    }

    @Test
    void theLv28400nProxyGatewayIsNotOnOurSideWhileTheEmptyStartIsTakenForTheMain() {
        LV28400NFixture icarus = new LV28400NFixture();
        icarus.baseData.assignEnemyMain(icarus.emptyStart, EnemyMainEvidence.MAIN_AREA, UnitType.Protoss_Pylon,
                LV28400NFixture.PROXY_PYLON);

        assertFalse(isOnOurSide(icarus, LV28400NFixture.PROXY_GATEWAY));
    }

    @Test
    void withTheMainUnknownTheLv28400nProxyGatewayIsOnOurSideOnceTheEmptyStartIsSeenEmpty() {
        LV28400NFixture icarus = new LV28400NFixture();
        icarus.baseData.markStartSeenEmpty(icarus.emptyStart);

        assertTrue(isOnOurSide(icarus, LV28400NFixture.PROXY_GATEWAY));
    }

    /**
     * An unscouted start nearer the Gateway than our main may still be the enemy's, so the Gateway could be its
     * home Gateway.
     */
    @Test
    void withTheMainUnknownTheLv28400nProxyGatewayIsNotOnOurSideWhileTheEmptyStartIsUnscouted() {
        LV28400NFixture icarus = new LV28400NFixture();

        assertFalse(isOnOurSide(icarus, LV28400NFixture.PROXY_GATEWAY));
    }

    /**
     * A razed enemy main is cleared and then seen empty, like the start that never held one. A home Gateway's
     * last known position beside it must still read as the enemy's side.
     */
    @Test
    void withEveryOtherStartSeenEmptyAHomeGatewayBesideTheRazedMainIsNotOnOurSide() {
        LV28400NFixture icarus = new LV28400NFixture();
        icarus.baseData.markStartSeenEmpty(icarus.emptyStart);
        icarus.baseData.markStartSeenEmpty(icarus.realMain);

        assertFalse(isOnOurSide(icarus, new Position(3584, 1424)));
        assertTrue(isOnOurSide(icarus, new Position(1600, 400)));
    }

    private static boolean isOnOurSide(LV28400NFixture icarus, Position position) {
        return StrategyDetectionContext.isOnOurSide(position, icarus.baseData, icarus.bases,
                (from, to) -> (int) from.getDistance(to));
    }
}
