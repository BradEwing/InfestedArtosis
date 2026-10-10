package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlordParkingTest {

    private static final Position MAIN = new Position(100, 100);
    private static final Position NEAR = new Position(500, 500);
    private static final Position FAR = new Position(2000, 2000);

    @Test
    void picksTheNearerOfTwoSpores() {
        List<Position> spores = Arrays.asList(FAR, NEAR);
        assertEquals(NEAR, OverlordParking.pickAnchor(new Position(600, 600), spores, MAIN, null));
        assertEquals(FAR, OverlordParking.pickAnchor(new Position(1900, 1900), spores, MAIN, null));
    }

    @Test
    void fallsBackToTheMainWithNoSpore() {
        assertEquals(MAIN, OverlordParking.pickAnchor(new Position(600, 600), Collections.emptyList(), MAIN, null));
    }

    @Test
    void aTieGoesToTheFirstListedSpore() {
        Position a = new Position(0, 100);
        Position b = new Position(200, 100);
        assertEquals(a, OverlordParking.pickAnchor(new Position(100, 100), Arrays.asList(a, b), MAIN, null));
    }

    @Test
    void reasonNamesWhyTheAnchorChanged() {
        List<Position> spores = Collections.singletonList(NEAR);
        assertEquals(OverlordParking.Reason.ASSIGNED, OverlordParking.reason(null, NEAR, spores, MAIN));
        assertEquals(OverlordParking.Reason.SPORE_COMPLETED, OverlordParking.reason(MAIN, NEAR, spores, MAIN));
        assertEquals(OverlordParking.Reason.NEARER_SPORE, OverlordParking.reason(NEAR, new Position(550, 550), spores, MAIN));
        assertEquals(OverlordParking.Reason.SPORE_LOST,
                OverlordParking.reason(FAR, NEAR, spores, MAIN));
        assertEquals(OverlordParking.Reason.SPORE_LOST,
                OverlordParking.reason(FAR, MAIN, Collections.emptyList(), MAIN));
        assertEquals(OverlordParking.Reason.OUT_OF_REACH, OverlordParking.reason(NEAR, MAIN, spores, MAIN));
    }

    @Test
    void nearestSporeDistanceIsMinusOneWithNone() {
        assertEquals(-1, OverlordParking.nearestSporeDistance(MAIN, Collections.emptyList()));
        assertEquals(500, OverlordParking.nearestSporeDistance(new Position(0, 0),
                Collections.singletonList(new Position(300, 400))), 1e-9);
    }

    private static List<OverlordParking.Decision> plan(Map<Integer, Position> parked, Map<Integer, Position> anchors,
                                                       List<Position> spores) {
        return OverlordParking.plan(parked, anchors, spores, MAIN, null, (id, anchor) -> parked.get(id).getDistance(anchor));
    }

    @Test
    void anOverlordNoLongerParkedGetsNoDecisionAndLosesItsAnchor() {
        Map<Integer, Position> anchors = new HashMap<>();
        anchors.put(1, NEAR);
        anchors.put(2, NEAR);
        Map<Integer, Position> parked = new LinkedHashMap<>();
        parked.put(1, new Position(600, 600));

        List<OverlordParking.Decision> decisions = plan(parked, anchors, Collections.singletonList(NEAR));

        assertEquals(1, decisions.size());
        assertEquals(1, decisions.get(0).unitId);
        assertEquals(Collections.singleton(1), anchors.keySet());
    }

    @Test
    void anOverlordAnchoredAtADeadSporeMovesOnWithSporeLost() {
        Map<Integer, Position> anchors = new HashMap<>();
        anchors.put(1, NEAR);
        Map<Integer, Position> parked = new LinkedHashMap<>();
        parked.put(1, new Position(600, 600));

        Position other = new Position(900, 700);
        OverlordParking.Decision toOther = plan(parked, anchors, Collections.singletonList(other)).get(0);
        assertEquals(other, toOther.anchor);
        assertEquals(OverlordParking.Reason.SPORE_LOST, toOther.reason);

        anchors.put(1, other);
        OverlordParking.Decision toMain = plan(parked, anchors, Collections.emptyList()).get(0);
        assertEquals(MAIN, toMain.anchor);
        assertEquals(OverlordParking.Reason.SPORE_LOST, toMain.reason);
    }

    @Test
    void anUnchangedAnchorProducesNoChange() {
        Map<Integer, Position> anchors = new HashMap<>();
        anchors.put(1, NEAR);
        Map<Integer, Position> parked = new LinkedHashMap<>();
        parked.put(1, new Position(600, 600));

        OverlordParking.Decision decision = plan(parked, anchors, Collections.singletonList(NEAR)).get(0);

        assertFalse(decision.changed());
        assertNull(decision.reason);
    }

    @Test
    void anOverlordIdlesWithinSixteenPixelsOfItsAnchorAndRalliesBeyond() {
        Map<Integer, Position> parked = new LinkedHashMap<>();
        parked.put(1, new Position(NEAR.getX() + 15, NEAR.getY()));
        parked.put(2, new Position(NEAR.getX() + 16, NEAR.getY()));

        List<OverlordParking.Decision> decisions = plan(parked, new HashMap<>(), Collections.singletonList(NEAR));

        assertTrue(decisions.get(0).idle);
        assertFalse(decisions.get(1).idle);
    }

    private static final Position NATURAL = new Position(900, 100);
    private static final Position NATURAL_SPORE = new Position(1000, 150);
    private static final Position THIRD_SPORE = new Position(1000, 900);

    @Test
    void aSporeBeyondTheCapIsNotAnAnchor() {
        Position farBase = new Position(100 + (int) OverlordParking.MAX_ANCHOR_DISTANCE + 200, 100);
        Position overlord = new Position(150, 120);
        assertEquals(MAIN, OverlordParking.pickAnchor(overlord, Collections.singletonList(farBase), MAIN, null));
    }

    @Test
    void aSporeJustWithinTheCapIsAnAnchor() {
        Position edge = new Position(100 + (int) OverlordParking.MAX_ANCHOR_DISTANCE - 10, 100);
        assertEquals(edge, OverlordParking.pickAnchor(MAIN, Collections.singletonList(edge), MAIN, null));
    }

    @Test
    void theNaturalsSporeIsPreferredOverANearerThirdBaseSpore() {
        Position overlord = new Position(1000, 700);
        List<Position> spores = Arrays.asList(THIRD_SPORE, NATURAL_SPORE);
        assertTrue(overlord.getDistance(THIRD_SPORE) < overlord.getDistance(NATURAL_SPORE));
        assertEquals(NATURAL_SPORE, OverlordParking.pickAnchor(overlord, spores, MAIN, NATURAL));
    }

    @Test
    void theNaturalsSporeIsReachableFromTheMainBeyondTheCap() {
        Position overlord = new Position(100, 100);
        assertTrue(overlord.getDistance(NATURAL_SPORE) > 0);
        assertEquals(NATURAL_SPORE,
                OverlordParking.pickAnchor(overlord, Collections.singletonList(NATURAL_SPORE), MAIN, NATURAL));
    }

    @Test
    void theNaturalsSporeIsCappedForAnOverlordFarFromTheMainAndTheNatural() {
        Position overlord = new Position(100 + 4000, 4000);
        assertEquals(MAIN,
                OverlordParking.pickAnchor(overlord, Collections.singletonList(NATURAL_SPORE), MAIN, NATURAL));
    }

    @Test
    void whenTheNaturalsSporeDiesTheNearestSporeWithinTheCapIsPickedElseTheMain() {
        Position overlord = new Position(1000, 700);
        assertEquals(THIRD_SPORE,
                OverlordParking.pickAnchor(overlord, Collections.singletonList(THIRD_SPORE), MAIN, NATURAL));
        assertEquals(MAIN,
                OverlordParking.pickAnchor(new Position(100, 100), Collections.singletonList(THIRD_SPORE), MAIN,
                        NATURAL));
    }

    @Test
    void aSporeAtAFarBaseLeavesAnOverlordBesideTheMainAtTheMain() {
        Position farSpore = new Position(3000, 3000);
        Map<Integer, Position> parked = new LinkedHashMap<>();
        parked.put(1, new Position(120, 120));
        OverlordParking.Decision decision = plan(parked, new HashMap<>(), Collections.singletonList(farSpore)).get(0);
        assertEquals(MAIN, decision.anchor);
        assertEquals(OverlordParking.Reason.ASSIGNED, decision.reason);
    }

    @Test
    void aPerchOverlordInTheSquadIsNotParked() {
        assertFalse(OverlordParking.isParked(true, UnitRole.PERCH));
        assertFalse(OverlordParking.isParked(true, UnitRole.SCOUT));
        assertFalse(OverlordParking.isParked(false, UnitRole.IDLE));
        assertTrue(OverlordParking.isParked(true, UnitRole.IDLE));
        assertTrue(OverlordParking.isParked(true, UnitRole.RALLY));
    }
}
