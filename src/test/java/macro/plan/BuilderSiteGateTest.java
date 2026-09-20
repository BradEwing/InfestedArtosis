package macro.plan;

import bwapi.TilePosition;
import info.BaseData;
import info.BuilderThreat;
import org.junit.jupiter.api.Test;
import util.Distance;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class BuilderSiteGateTest {

    private static final TilePosition MAIN_HATCHERY = new TilePosition(45, 9);
    private static final TilePosition NATURAL_CREEP = new TilePosition(75, 5);
    private static final Set<TilePosition> MAIN_TILES = Distance.tilesWithinManhattanDistance(MAIN_HATCHERY, 12);

    @Test
    void aBuilderIsHeldFromASiteWithKnownEnemies() {
        assertSame(BuilderDispatchDecision.HOLD_SITE_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(0, 6, 0, false)));
    }

    @Test
    void aBuilderIsDispatchedToASiteWithNoKnownEnemiesOnAClearRoute() {
        assertSame(BuilderDispatchDecision.DISPATCH, PlanManager.dispatchDecision(BuilderThreat.NONE));
    }

    /**
     * IA-381: the bypass that let a builder standing on a contested site proceed is gone. Sixteen
     * colony builders died to it inside a main that was being overrun.
     */
    @Test
    void aBuilderAlreadyAtAContestedSiteIsHeld() {
        assertSame(BuilderDispatchDecision.HOLD_SITE_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(0, 6, 0, true)));
    }

    @Test
    void aBuilderIsHeldFromAClearSiteAcrossAContestedRoute() {
        assertSame(BuilderDispatchDecision.HOLD_PATH_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(3, 0, 0, false)));
    }

    @Test
    void aBuilderIsHeldFromARouteEnemyStaticDefenceCovers() {
        assertSame(BuilderDispatchDecision.HOLD_PATH_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(0, 0, 1, false)));
    }

    /** The site is the more specific answer, so it names the hold when both are hot. */
    @Test
    void aSiteThreatOutranksARouteThreat() {
        assertSame(BuilderDispatchDecision.HOLD_SITE_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(3, 6, 2, false)));
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
