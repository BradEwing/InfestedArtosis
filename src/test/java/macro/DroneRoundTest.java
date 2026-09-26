package macro;

import bwapi.UnitType;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneRoundTest {

    private static final int FRAME = 8000;

    private static final int DRONES = 12;

    private static final int CAP = 27;

    private static final boolean CALM = false;

    private static final boolean WANTED = true;

    private static final boolean SATURATED = false;

    private static final boolean THREAT = true;

    private DroneRound openRound() {
        DroneRound round = new DroneRound();
        round.update(FRAME, DroneRound.FIRST_ROUND_ARMY_UNITS, DRONES, CAP, WANTED, CALM);
        return round;
    }

    @Test
    void noRoundOpensBelowTheFirstArmyMilestone() {
        DroneRound round = new DroneRound();

        round.update(FRAME, DroneRound.FIRST_ROUND_ARMY_UNITS - 1, DRONES, CAP, WANTED, CALM);

        assertFalse(round.isActive());
    }

    @Test
    void aRoundOpensAtTheFirstArmyMilestoneWithTheNextDroneTarget() {
        DroneRound round = openRound();

        assertTrue(round.isActive());
        assertEquals(DRONES + DroneRound.DRONES_PER_ROUND, round.getDroneTarget());
    }

    @Test
    void theRoundTargetStopsAtTheBuildsDroneCap() {
        DroneRound round = new DroneRound();

        round.update(FRAME, DroneRound.FIRST_ROUND_ARMY_UNITS, CAP - 1, CAP, WANTED, CALM);

        assertTrue(round.isActive());
        assertEquals(CAP, round.getDroneTarget());
    }

    @Test
    void noRoundOpensOnceTheBuildsDroneCapIsMet() {
        DroneRound round = new DroneRound();

        round.update(FRAME, DroneRound.FIRST_ROUND_ARMY_UNITS, CAP, CAP, WANTED, CALM);

        assertFalse(round.isActive());
    }

    @Test
    void aBuildThatRunsNoRoundsNeverOpensOne() {
        DroneRound round = new DroneRound();

        round.update(FRAME, 40, DRONES, 0, WANTED, CALM);

        assertFalse(round.isActive());
    }

    @Test
    void theRoundStaysOpenUntilItsDronesAreCounted() {
        DroneRound round = openRound();
        int target = round.getDroneTarget();

        round.update(FRAME + 100, DroneRound.FIRST_ROUND_ARMY_UNITS, target - 1, CAP, WANTED, CALM);
        assertTrue(round.isActive());

        round.update(FRAME + 200, DroneRound.FIRST_ROUND_ARMY_UNITS + 1, target, CAP, WANTED, CALM);
        assertFalse(round.isActive());
        assertEquals(DroneRound.FIRST_ROUND_ARMY_UNITS + 1 + DroneRound.ARMY_UNITS_PER_ROUND, round.getArmyMilestone());
    }

    @Test
    void theNextRoundWaitsForTheNextArmyMilestone() {
        DroneRound round = openRound();
        round.update(FRAME + 200, DroneRound.FIRST_ROUND_ARMY_UNITS, round.getDroneTarget(), CAP, WANTED, CALM);
        int milestone = round.getArmyMilestone();

        round.update(FRAME + 300, milestone - 1, DRONES + 4, CAP, WANTED, CALM);
        assertFalse(round.isActive());

        round.update(FRAME + 400, milestone, DRONES + 4, CAP, WANTED, CALM);
        assertTrue(round.isActive());
    }

    @Test
    void aRoundThatCannotFinishClosesAfterTheLongestRound() {
        DroneRound round = openRound();

        round.update(FRAME + DroneRound.MAX_ROUND_FRAMES - 1, 8, DRONES, CAP, WANTED, CALM);
        assertTrue(round.isActive());

        round.update(FRAME + DroneRound.MAX_ROUND_FRAMES, 8, DRONES, CAP, WANTED, CALM);
        assertFalse(round.isActive());
        assertEquals(8 + DroneRound.ARMY_UNITS_PER_ROUND, round.getArmyMilestone());
    }

    @Test
    void aThreatClosesTheRoundAndItReopensOnceTheThreatClears() {
        DroneRound round = openRound();

        round.update(FRAME + 10, DroneRound.FIRST_ROUND_ARMY_UNITS, DRONES, CAP, WANTED, THREAT);
        assertFalse(round.isActive());
        assertEquals(DroneRound.FIRST_ROUND_ARMY_UNITS, round.getArmyMilestone());

        round.update(FRAME + 20, DroneRound.FIRST_ROUND_ARMY_UNITS, DRONES, CAP, WANTED, THREAT);
        assertFalse(round.isActive());

        round.update(FRAME + 30, DroneRound.FIRST_ROUND_ARMY_UNITS, DRONES, CAP, WANTED, CALM);
        assertTrue(round.isActive());
    }

    @Test
    void noRoundOpensWhileTheWorkerGatesWantNoDrone() {
        DroneRound round = new DroneRound();

        round.update(FRAME, DroneRound.FIRST_ROUND_ARMY_UNITS, DRONES, CAP, SATURATED, CALM);

        assertFalse(round.isActive());
        assertEquals(DroneRound.FIRST_ROUND_ARMY_UNITS, round.getArmyMilestone());
    }

    @Test
    void theRoundClosesOnceTheWorkerGatesWantNoDrone() {
        DroneRound round = openRound();

        round.update(FRAME + 10, 7, DRONES + 1, CAP, SATURATED, CALM);

        assertFalse(round.isActive());
        assertEquals(7 + DroneRound.ARMY_UNITS_PER_ROUND, round.getArmyMilestone());
    }

    @Test
    void noRoundOpensUnderAThreat() {
        DroneRound round = new DroneRound();

        round.update(FRAME, DroneRound.FIRST_ROUND_ARMY_UNITS, DRONES, CAP, WANTED, THREAT);

        assertFalse(round.isActive());
    }

    @Test
    void aRoundLeftOpenByAPreviousBuildClosesOnAZeroCap() {
        DroneRound round = openRound();

        round.update(FRAME + 1, 0, DRONES, 0, WANTED, CALM);

        assertFalse(round.isActive());
    }

    @Test
    void aRushAnAllInOrAnEnemyAtOurBasesIsAThreat() {
        assertFalse(DroneRound.isThreatened(false, false, 0));
        assertTrue(DroneRound.isThreatened(true, false, 0));
        assertTrue(DroneRound.isThreatened(false, true, 0));
        assertTrue(DroneRound.isThreatened(false, false, 1));
    }

    @Test
    void onlyADroneAtTheRoundPriorityIsARoundDrone() {
        assertTrue(DroneRound.isRoundDrone(new UnitPlan(UnitType.Zerg_Drone, UnitPlan.DRONE_ROUND_PRIORITY)));
        assertFalse(DroneRound.isRoundDrone(new UnitPlan(UnitType.Zerg_Drone, 6332)));
        assertFalse(DroneRound.isRoundDrone(new UnitPlan(UnitType.Zerg_Zergling, UnitPlan.DRONE_ROUND_PRIORITY)));
    }

    @Test
    void theRoundBandPollsAheadOfTheAdvancedUnitBand() {
        assertTrue(UnitPlan.DRONE_ROUND_PRIORITY < UnitPlan.ADVANCED_UNIT_PRIORITY - 1);
    }
}
