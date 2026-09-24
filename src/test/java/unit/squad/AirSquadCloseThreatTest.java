package unit.squad;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirSquadCloseThreatTest {

    private static final boolean AIR = true;
    private static final boolean GROUND = false;
    private static final boolean CLOSE_THREATS = true;
    private static final boolean NEAR_HOME = true;
    private static final boolean AWAY = false;
    private static final boolean COMMITTED = true;
    private static final boolean UNCOMMITTED = false;
    private static final double RALLY_DISTANCE = 70;
    private static final double FAR_FROM_RALLY = 3903;
    private static final int ONE_MUTALISK = 1;
    private static final int THRESHOLD = SquadManager.airMoveOutThreshold(false, Race.Protoss);

    private static SquadManager.SquadAction decide(boolean airSquad, boolean nearHome, int strength, int threshold,
                                                   SquadStatus status, boolean committed) {
        boolean holdAway = SquadManager.holdsAwayFromHome(airSquad, CLOSE_THREATS, nearHome, strength, threshold,
                committed);
        return SquadManager.chooseSquadAction(holdAway, CLOSE_THREATS, strength, threshold, status, committed,
                RALLY_DISTANCE);
    }

    @Test
    void aSubThresholdAirSquadWithCloseThreatsAwayFromHomeRallies() {
        assertEquals(SquadManager.SquadAction.RALLY,
                decide(AIR, AWAY, ONE_MUTALISK, THRESHOLD, SquadStatus.RALLY, UNCOMMITTED));
        assertEquals(SquadManager.SquadAction.RALLY,
                decide(AIR, AWAY, THRESHOLD - 1, THRESHOLD, SquadStatus.RALLY, UNCOMMITTED));
    }

    @Test
    void theSameSquadWithCloseThreatsAtHomeSimulates() {
        assertEquals(SquadManager.SquadAction.SIMULATE,
                decide(AIR, NEAR_HOME, ONE_MUTALISK, THRESHOLD, SquadStatus.RALLY, UNCOMMITTED));
    }

    @Test
    void aCommittedSubThresholdAirSquadStillSimulatesAwayFromHome() {
        assertEquals(SquadManager.SquadAction.SIMULATE,
                decide(AIR, AWAY, ONE_MUTALISK, THRESHOLD, SquadStatus.FIGHT, COMMITTED));
        assertEquals(SquadManager.SquadAction.SIMULATE,
                decide(AIR, AWAY, ONE_MUTALISK, THRESHOLD, SquadStatus.RETREAT, COMMITTED));
    }

    @Test
    void anAirSquadAtTheThresholdBehavesAsBefore() {
        assertEquals(SquadManager.SquadAction.SIMULATE,
                decide(AIR, AWAY, THRESHOLD, THRESHOLD, SquadStatus.RALLY, UNCOMMITTED));
        assertEquals(SquadManager.SquadAction.SIMULATE,
                decide(AIR, AWAY, THRESHOLD + 1, THRESHOLD, SquadStatus.RALLY, UNCOMMITTED));
        assertEquals(SquadManager.SquadAction.LAUNCH, SquadManager.chooseSquadAction(false, false, THRESHOLD,
                THRESHOLD, SquadStatus.RALLY, UNCOMMITTED, RALLY_DISTANCE));
    }

    @Test
    void noCloseThreatsIsNeverHeldAway() {
        assertFalse(SquadManager.holdsAwayFromHome(AIR, false, AWAY, ONE_MUTALISK, THRESHOLD, UNCOMMITTED));
        assertEquals(SquadManager.SquadAction.RALLY, SquadManager.chooseSquadAction(false, false, ONE_MUTALISK,
                THRESHOLD, SquadStatus.RALLY, UNCOMMITTED, RALLY_DISTANCE));
    }

    @Test
    void aGroundSquadWithCloseThreatsSimulatesWhereverItStands() {
        int groundFloor = 4;
        for (boolean nearHome : new boolean[]{NEAR_HOME, AWAY}) {
            for (boolean committed : new boolean[]{COMMITTED, UNCOMMITTED}) {
                assertFalse(SquadManager.holdsAwayFromHome(GROUND, CLOSE_THREATS, nearHome, 1, groundFloor,
                        committed));
                assertEquals(SquadManager.SquadAction.SIMULATE,
                        decide(GROUND, nearHome, 1, groundFloor, SquadStatus.RALLY, committed));
            }
        }
    }

    @Test
    void theHoldAwayOverloadDefersToTheBaseDecisionWhenNotHolding() {
        for (SquadStatus status : new SquadStatus[]{SquadStatus.RALLY, SquadStatus.FIGHT, SquadStatus.RETREAT}) {
            for (boolean committed : new boolean[]{COMMITTED, UNCOMMITTED}) {
                for (double distance : new double[]{RALLY_DISTANCE, FAR_FROM_RALLY}) {
                    assertEquals(
                            SquadManager.chooseSquadAction(false, ONE_MUTALISK, THRESHOLD, status, committed, distance),
                            SquadManager.chooseSquadAction(false, false, ONE_MUTALISK, THRESHOLD, status, committed,
                                    distance));
                }
            }
        }
    }

    @Test
    void aHatchlingInANewSquadUnderThreatAwayFromHomeRalliesInsteadOfSimulating() {
        boolean holdAway = SquadManager.holdsAwayFromHome(AIR, CLOSE_THREATS, AWAY, ONE_MUTALISK, THRESHOLD,
                UNCOMMITTED);

        assertTrue(holdAway);
        assertEquals(SquadManager.ReinforcementPath.HOLD_AWAY,
                SquadManager.reinforcementPath(SquadStatus.RALLY, false, holdAway));
    }

    @Test
    void aHatchlingInANewSquadUnderThreatAtHomeStillSimulates() {
        boolean holdAway = SquadManager.holdsAwayFromHome(AIR, CLOSE_THREATS, NEAR_HOME, ONE_MUTALISK, THRESHOLD,
                UNCOMMITTED);

        assertFalse(holdAway);
        assertEquals(SquadManager.ReinforcementPath.SIMULATE,
                SquadManager.reinforcementPath(SquadStatus.RALLY, false, holdAway));
    }

    @Test
    void stagingAndContainmentTakePrecedenceOverTheHold() {
        assertEquals(SquadManager.ReinforcementPath.STAGE,
                SquadManager.reinforcementPath(SquadStatus.RALLY, true, true));
        assertEquals(SquadManager.ReinforcementPath.JOIN_CONTAINMENT,
                SquadManager.reinforcementPath(SquadStatus.CONTAIN, false, true));
    }

    @Test
    void aSquadFailingTheCheapTermsIsNeverHeldWhateverTheCostlyTermsSay() {
        assertTrue(SquadManager.mayHoldAwayFromHome(AIR, ONE_MUTALISK, THRESHOLD, UNCOMMITTED));
        assertFalse(SquadManager.mayHoldAwayFromHome(AIR, ONE_MUTALISK, THRESHOLD, COMMITTED));
        assertFalse(SquadManager.mayHoldAwayFromHome(AIR, THRESHOLD, THRESHOLD, UNCOMMITTED));
        assertFalse(SquadManager.mayHoldAwayFromHome(GROUND, ONE_MUTALISK, THRESHOLD, UNCOMMITTED));

        for (boolean airSquad : new boolean[]{AIR, GROUND}) {
            for (int strength : new int[]{ONE_MUTALISK, THRESHOLD}) {
                for (boolean committed : new boolean[]{COMMITTED, UNCOMMITTED}) {
                    if (SquadManager.mayHoldAwayFromHome(airSquad, strength, THRESHOLD, committed)) {
                        continue;
                    }
                    for (boolean closeThreats : new boolean[]{true, false}) {
                        for (boolean nearHome : new boolean[]{NEAR_HOME, AWAY}) {
                            assertFalse(SquadManager.holdsAwayFromHome(airSquad, closeThreats, nearHome, strength,
                                    THRESHOLD, committed));
                        }
                    }
                }
            }
        }
    }

    @Test
    void theHomeRadiusIsMeasuredOnTheGroundWithAirAsAFallback() {
        int radius = SquadManager.AIR_HOME_DEFENSE_RADIUS;

        assertTrue(SquadManager.isInsideHomeDefenseRadius(radius, Double.MAX_VALUE));
        assertFalse(SquadManager.isInsideHomeDefenseRadius(radius + 1, 0));
        assertTrue(SquadManager.isInsideHomeDefenseRadius(-1, radius));
        assertFalse(SquadManager.isInsideHomeDefenseRadius(-1, radius + 1));
        assertFalse(SquadManager.isInsideHomeDefenseRadius(-1, Double.MAX_VALUE));
    }

    @Test
    void aNewAirUnitJoinsARetreatingSquadBesideIt() {
        double near = SquadManager.AIR_JOIN_DISTANCE - 1;
        double far = SquadManager.AIR_JOIN_DISTANCE;

        assertTrue(SquadManager.mayJoinAirSquadAt(SquadStatus.RETREAT, near));
        assertFalse(SquadManager.mayJoinAirSquadAt(SquadStatus.RETREAT, far));
        assertTrue(SquadManager.mayJoinAirSquadAt(SquadStatus.FIGHT, near));
        assertFalse(SquadManager.mayJoinAirSquadAt(SquadStatus.FIGHT, far));
        assertTrue(SquadManager.mayJoinAirSquadAt(SquadStatus.RALLY, FAR_FROM_RALLY));
        assertFalse(SquadManager.mayJoinAirSquadAt(SquadStatus.CONTAIN, near));
        assertFalse(SquadManager.mayJoinAirSquadAt(SquadStatus.RUNBY, near));
    }
}
