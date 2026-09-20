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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderSiteGateTest {

    private static final TilePosition MAIN_HATCHERY = new TilePosition(45, 9);
    private static final TilePosition NATURAL_CREEP = new TilePosition(75, 5);
    private static final Set<TilePosition> MAIN_TILES = Distance.tilesWithinManhattanDistance(MAIN_HATCHERY, 12);

    @Test
    void aBuilderIsHeldFromASiteWithKnownEnemies() {
        assertSame(BuilderDispatchDecision.HOLD_SITE_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(0, 6, 0, false, false, false)));
    }

    @Test
    void aBuilderIsDispatchedToASiteWithNoKnownEnemiesOnAClearRoute() {
        assertSame(BuilderDispatchDecision.DISPATCH, PlanManager.dispatchDecision(BuilderThreat.NONE));
    }

    @Test
    void aBuilderAlreadyAtAContestedRemoteSiteIsHeld() {
        assertSame(BuilderDispatchDecision.HOLD_SITE_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(0, 6, 0, true, false, false)));
    }

    @Test
    void aBuilderIsHeldFromAClearSiteAcrossAContestedRoute() {
        assertSame(BuilderDispatchDecision.HOLD_PATH_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(3, 0, 0, false, false, false)));
    }

    @Test
    void aBuilderIsHeldFromARouteEnemyStaticDefenceCovers() {
        assertSame(BuilderDispatchDecision.HOLD_PATH_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(0, 0, 1, false, false, false)));
    }

    /** The site is the more specific answer, so it names the hold when both are hot. */
    @Test
    void aSiteThreatOutranksARouteThreat() {
        assertSame(BuilderDispatchDecision.HOLD_SITE_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(3, 6, 2, false, false, false)));
    }

    /**
     * IA-381: a creep colony at a base we hold is the seed of the sunken the enemies at that base
     * are the reason for, so the gate waves it through.
     */
    @Test
    void aBuilderIsDispatchedToAThreatenedSiteAtOneOfOurBases() {
        assertSame(BuilderDispatchDecision.DISPATCH_HOME_SITE,
                PlanManager.dispatchDecision(new BuilderThreat(0, 6, 0, false, true, true)));
    }

    @Test
    void aBuilderIsDispatchedToASiteAtOneOfOurBasesAcrossAContestedRoute() {
        assertSame(BuilderDispatchDecision.DISPATCH_HOME_SITE,
                PlanManager.dispatchDecision(new BuilderThreat(4, 6, 2, false, true, true)));
    }

    /** A quiet home site is an ordinary dispatch, so the carve-out only names holds it overrode. */
    @Test
    void aQuietSiteAtOneOfOurBasesIsAnOrdinaryDispatch() {
        assertSame(BuilderDispatchDecision.DISPATCH,
                PlanManager.dispatchDecision(new BuilderThreat(0, 0, 0, false, true, true)));
    }

    /**
     * The carve-out needs both ends of the walk on ground we hold. A base whose own drones are all
     * carrying, on gas or already building hands the plan to the nearest drone anywhere on the map,
     * and that drone is the lone builder setting out across it that the gate exists to stop.
     */
    @Test
    void aBuilderFromOffOurGroundIsHeldEvenWhenTheSiteIsAtOneOfOurBases() {
        assertSame(BuilderDispatchDecision.HOLD_SITE_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(0, 6, 0, false, true, false)));
    }

    @Test
    void aBuilderOnOurGroundIsStillHeldFromAContestedSiteWeDoNotHold() {
        assertSame(BuilderDispatchDecision.HOLD_SITE_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(0, 6, 0, false, false, true)));
    }

    @Test
    void aBuilderOnOurGroundIsStillHeldFromAContestedRouteToASiteWeDoNotHold() {
        assertSame(BuilderDispatchDecision.HOLD_PATH_THREAT,
                PlanManager.dispatchDecision(new BuilderThreat(3, 0, 1, false, false, true)));
    }

    @Test
    void everyDispatchDecisionSendsTheBuilderAndEveryHoldDoesNot() {
        assertTrue(BuilderDispatchDecision.DISPATCH.isDispatch());
        assertTrue(BuilderDispatchDecision.DISPATCH_HOME_SITE.isDispatch());
        assertFalse(BuilderDispatchDecision.HOLD_SITE_THREAT.isDispatch());
        assertFalse(BuilderDispatchDecision.HOLD_PATH_THREAT.isDispatch());
        assertFalse(BuilderDispatchDecision.RECALLED.isDispatch());
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
