package macro.plan;

import bwapi.TilePosition;
import info.BaseData;
import org.junit.jupiter.api.Test;
import util.Distance;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderSiteGateTest {

    private static final TilePosition MAIN_HATCHERY = new TilePosition(45, 9);
    private static final TilePosition NATURAL_CREEP = new TilePosition(75, 5);
    private static final Set<TilePosition> MAIN_TILES = Distance.tilesWithinManhattanDistance(MAIN_HATCHERY, 12);

    @Test
    void aBuilderIsHeldFromASiteWithKnownEnemies() {
        assertTrue(PlanManager.shouldHoldBuilder(6, false));
    }

    @Test
    void aBuilderIsDispatchedToASiteWithNoKnownEnemies() {
        assertFalse(PlanManager.shouldHoldBuilder(0, false));
    }

    @Test
    void aBuilderAlreadyAtTheSiteIsNotHeld() {
        assertFalse(PlanManager.shouldHoldBuilder(6, true));
    }

    @Test
    void aSiteInTheMainIsContestedAcrossTheWholeMain() {
        assertSame(MAIN_TILES, BaseData.siteTiles(MAIN_TILES, MAIN_HATCHERY, BaseData.NATURAL_DEFENSE_TILE_RADIUS));
    }

    @Test
    void aSiteOutsideTheMainIsContestedWithinTheRadius() {
        Set<TilePosition> tiles = BaseData.siteTiles(MAIN_TILES, NATURAL_CREEP, BaseData.NATURAL_DEFENSE_TILE_RADIUS);

        assertEquals(Distance.tilesWithinManhattanDistance(NATURAL_CREEP, BaseData.NATURAL_DEFENSE_TILE_RADIUS), tiles);
        assertFalse(tiles.contains(MAIN_HATCHERY));
    }
}
