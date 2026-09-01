package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadCommitmentTest {

    private static final int GROUND_FLOOR = 4;
    private static final int LURKER_FLOOR = 1;
    private static final double FAR_FROM_HOME = 3903;
    private static final double AT_HOME = 70;

    @Test
    void splitIsRefusedWhenEitherSideFallsBelowTheFloor() {
        assertFalse(SquadManager.splitKeepsBothSidesCommittable(6, 2, GROUND_FLOOR));
        assertFalse(SquadManager.splitKeepsBothSidesCommittable(6, 4, GROUND_FLOOR));
        assertFalse(SquadManager.splitKeepsBothSidesCommittable(4, 1, GROUND_FLOOR));
    }

    @Test
    void splitIsAllowedWhenBothSidesStayAboveTheFloor() {
        assertTrue(SquadManager.splitKeepsBothSidesCommittable(8, 4, GROUND_FLOOR));
        assertTrue(SquadManager.splitKeepsBothSidesCommittable(12, 5, GROUND_FLOOR));
    }

    @Test
    void lurkerSquadsStillSplitOnTheirOwnFloor() {
        assertTrue(SquadManager.splitKeepsBothSidesCommittable(8, 2, LURKER_FLOOR));
        assertFalse(SquadManager.splitKeepsBothSidesCommittable(8, 0, LURKER_FLOOR));
    }

    @Test
    void squadAboveTheFloorLaunches() {
        assertEquals(SquadManager.SquadAction.LAUNCH,
                SquadManager.chooseSquadAction(false, 6, GROUND_FLOOR, SquadStatus.RALLY, false, AT_HOME));
    }

    @Test
    void uncommittedSquadBelowTheFloorStillRallies() {
        assertEquals(SquadManager.SquadAction.RALLY,
                SquadManager.chooseSquadAction(false, 2, GROUND_FLOOR, SquadStatus.RALLY, false, FAR_FROM_HOME));
    }

    @Test
    void committedSquadBackHomeIsReleasedToRally() {
        assertEquals(SquadManager.SquadAction.RALLY,
                SquadManager.chooseSquadAction(false, 2, GROUND_FLOOR, SquadStatus.RETREAT, true, AT_HOME));
    }

    @Test
    void committedSquadFarFromHomeKeepsActingBelowTheFloor() {
        assertEquals(SquadManager.SquadAction.SIMULATE,
                SquadManager.chooseSquadAction(false, 2, GROUND_FLOOR, SquadStatus.RETREAT, true, FAR_FROM_HOME));
    }

    @Test
    void closeThreatsOutrankEveryThreshold() {
        assertEquals(SquadManager.SquadAction.SIMULATE,
                SquadManager.chooseSquadAction(true, 1, 40, SquadStatus.RALLY, false, AT_HOME));
        assertEquals(SquadManager.SquadAction.SIMULATE,
                SquadManager.chooseSquadAction(true, 1, 40, SquadStatus.RETREAT, false, FAR_FROM_HOME));
    }

    @Test
    void emergencyFloorsStillLaunchWithSmallNumbers() {
        assertEquals(SquadManager.SquadAction.LAUNCH,
                SquadManager.chooseSquadAction(false, 1, LURKER_FLOOR, SquadStatus.RALLY, false, AT_HOME));
        assertEquals(SquadManager.SquadAction.LAUNCH,
                SquadManager.chooseSquadAction(false, 2, 2, SquadStatus.RALLY, false, AT_HOME));
    }

    @Test
    void siblingInheritsCommitmentRallyPointAndStatus() {
        Squad parent = new GroundSquad();
        parent.setStatus(SquadStatus.FIGHT);
        parent.setRallyPoint(new Position(320, 480));
        parent.commit(4200);

        Squad child = parent.createSibling();
        child.inheritStateFrom(parent);

        assertTrue(child.isGroundSquad());
        assertTrue(child.isCommitted());
        assertEquals(4200, child.getCommitFrame());
        assertEquals(SquadStatus.FIGHT, child.getStatus());
        assertEquals(parent.getRallyPoint(), child.getRallyPoint());
    }

    @Test
    void commitmentKeepsTheEarliestClearanceAndIsClearedOnRecall() {
        Squad squad = new GroundSquad();
        squad.commit(4200);
        squad.commit(4250);

        assertEquals(4200, squad.getCommitFrame());

        squad.clearCommitment();

        assertFalse(squad.isCommitted());
    }

    /**
     * Reproduces KNQP200G frames 4200 to 4300: six Zerglings cleared to advance on an empty natural, split into
     * sub floor fragments ~3,900px from home on the following two decision ticks.
     */
    @Test
    void splitFragmentsFarFromHomeAreNotAllRecalled() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.FIGHT);
        squad.commit(4200);

        assertFalse(SquadManager.splitKeepsBothSidesCommittable(6, 2, GROUND_FLOOR),
                "frame 4250: splitting 6 supply into 2 and 4 drops both sides under the floor");
        assertFalse(SquadManager.splitKeepsBothSidesCommittable(4, 1, GROUND_FLOOR),
                "frame 4300: splitting 4 supply into 1 and 3 drops both sides under the floor");

        Squad child = squad.createSibling();
        child.inheritStateFrom(squad);

        for (int fragmentStrength = 1; fragmentStrength < GROUND_FLOOR; fragmentStrength++) {
            assertEquals(SquadManager.SquadAction.SIMULATE,
                    SquadManager.chooseSquadAction(false, fragmentStrength, GROUND_FLOOR, child.getStatus(),
                            child.isCommitted(), FAR_FROM_HOME),
                    "a fragment of " + fragmentStrength + " supply must not be recalled on the tick it is created");
        }
    }
}
