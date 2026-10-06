package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.managed.Lurker;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LurkerFireZonesTest {

    private static final int RANGE = 192;
    private static final Position BUNKER = new Position(1000, 1000);

    @Test
    void aBunkerWithinTheLurkersRangeIsAnswered() {
        StaticDefenseZone bunker = new StaticDefenseZone(UnitType.Terran_Bunker, BUNKER, 224);

        List<StaticDefenseZone> zones = SquadManager.unanswered(Collections.singletonList(bunker),
                new Position(1000, 1000 + 100), RANGE);

        assertEquals(Collections.emptyList(), zones);
    }

    @Test
    void aSiegedTankBeyondTheLurkersRangeIsNotAnswered() {
        StaticDefenseZone tank = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode, BUNKER, 384);

        List<StaticDefenseZone> zones = SquadManager.unanswered(Collections.singletonList(tank),
                new Position(1000, 1000 + 300), RANGE);

        assertSame(tank, zones.get(0));
    }

    @Test
    void onlyTheZonesBeyondTheRangeAreKept() {
        StaticDefenseZone near = new StaticDefenseZone(UnitType.Terran_Bunker, BUNKER, 224);
        StaticDefenseZone far = new StaticDefenseZone(UnitType.Terran_Bunker, new Position(1000, 1600), 224);

        List<StaticDefenseZone> zones = SquadManager.unanswered(Arrays.asList(near, far),
                new Position(1000, 1100), RANGE);

        assertEquals(Collections.singletonList(far), zones);
    }

    private static final Position EDGE_LURKER = new Position(111, 500);
    private static final Position RALLY = new Position(700, 500);
    private static final int PADDING = 32;

    @Test
    void aSafePointNeverLiesAwayFromTheRallyPoint() {
        StaticDefenseZone mark = new StaticDefenseZone(UnitType.Zerg_Lurker, new Position(400, 500), 192);

        Position point = RunbyTargeting.findClearPoint(new Position(400, 500), Collections.singletonList(mark), PADDING,
                SquadManager.LURKER_SAFE_CLEARANCE, 0, RALLY, position -> true, RALLY);

        assertTrue(point.getDistance(RALLY) < new Position(400, 500).getDistance(RALLY));
    }

    @Test
    void withOnlyPointsAlongTheMapEdgeAwayFromTheRallyPointTheSafePointFallsBackToTheRallyPoint() {
        StaticDefenseZone mark = new StaticDefenseZone(UnitType.Zerg_Lurker, new Position(160, 500), 192);
        Position clear = RunbyTargeting.findClearPoint(EDGE_LURKER, Collections.singletonList(mark), PADDING,
                SquadManager.LURKER_SAFE_CLEARANCE, 0, RALLY, position -> position.getX() < EDGE_LURKER.getX(), RALLY);

        assertNull(clear);
        assertSame(RALLY, SquadManager.retreatSidePoint(clear, EDGE_LURKER, RALLY));
    }

    @Test
    void aClearPointOnTheRetreatSideIsKept() {
        Position clear = new Position(300, 500);

        assertSame(clear, SquadManager.retreatSidePoint(clear, EDGE_LURKER, RALLY));
    }

    @Test
    void aLurkerNearTheRallyPointWithNoClearPointHasNoSafePoint() {
        Position nearRally = new Position(RALLY.getX() - Lurker.SAFE_POINT_NEAR_DISTANCE, RALLY.getY());

        assertNull(SquadManager.retreatSidePoint(null, nearRally, RALLY));
    }

    @Test
    void aLurkerWithNoRallyPointAndNoClearPointHasNoSafePoint() {
        assertNull(SquadManager.retreatSidePoint(null, EDGE_LURKER, null));
    }
}
