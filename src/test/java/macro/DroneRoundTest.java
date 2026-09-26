package macro;

import bwapi.UnitType;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;
import unit.squad.ContainHeldTimer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    private static final int HELD_AT = FRAME - ContainHeldTimer.HELD_FRAMES;

    private static final int WORKERS = 20;

    private static final int SOFT_CAP = 30;

    private static final int HARD_CAP = 40;

    private static final int NO_ARMY = 0;

    private final List<String> reports = new ArrayList<>();

    private static DroneRound.ContainHeld.ContainHeldBuilder held() {
        return DroneRound.ContainHeld.builder()
                .eligible(true)
                .chainStartFrame(HELD_AT)
                .heldFrames(ContainHeldTimer.HELD_FRAMES)
                .hatcheries(2)
                .workers(WORKERS)
                .softCap(SOFT_CAP)
                .hardCap(HARD_CAP);
    }

    private static DroneRound.ContainHeld heldAt(int frame) {
        return held().heldFrames(frame - HELD_AT).build();
    }

    private DroneRound openContainHeldRound() {
        DroneRound round = new DroneRound();
        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, held().build());
        return round;
    }

    private PlanEventSink recorder() {
        return new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
            }

            @Override
            public void onDroneRoundOpened(DroneRound.Report report) {
                reports.add("OPEN:" + report.getKind() + ":" + report.getReason() + ":" + report.getSize());
            }

            @Override
            public void onDroneRoundClosed(DroneRound.Report report) {
                reports.add("CLOSE:" + report.getKind() + ":" + report.getReason() + ":" + report.getDrones());
            }
        };
    }

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    @Test
    void aHeldContainOpensARoundWithNoArmyMilestone() {
        DroneRound round = openContainHeldRound();

        assertTrue(round.isActive());
        assertEquals(DroneRound.OpenReason.CONTAIN_HELD, round.getReason());
    }

    @Test
    void theContainHeldRoundIsAtLeastThreeDrones() {
        assertEquals(3, DroneRound.containHeldRoundSize(1));
        assertEquals(3, DroneRound.containHeldRoundSize(2));
        assertEquals(3, DroneRound.containHeldRoundSize(3));
        assertEquals(5, DroneRound.containHeldRoundSize(5));
    }

    @Test
    void theContainHeldRoundTargetIsOneDronePerHatcheryPastTheDronesItOpenedOn() {
        DroneRound twoHatcheries = openContainHeldRound();
        assertEquals(DRONES + 3, twoHatcheries.getDroneTarget());
        assertEquals(3, twoHatcheries.getRoundSize());

        DroneRound fiveHatcheries = new DroneRound();
        fiveHatcheries.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, held().hatcheries(5).build());
        assertEquals(DRONES + 5, fiveHatcheries.getDroneTarget());
    }

    @Test
    void theBuildsDroneCapDoesNotBoundAContainHeldRound() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, CAP, CAP, WANTED, CALM, held().build());
        assertTrue(round.isActive());
        assertEquals(CAP + 3, round.getDroneTarget());

        round.update(FRAME + 1, NO_ARMY, CAP + 1, CAP, WANTED, CALM, heldAt(FRAME + 1));
        assertTrue(round.isActive());
    }

    @Test
    void noContainHeldRoundOpensBeforeTheContainIsHeld() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM,
                held().heldFrames(ContainHeldTimer.HELD_FRAMES - 1).build());
        assertFalse(round.isActive());

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM,
                held().chainStartFrame(ContainHeldTimer.NO_CHAIN).build());
        assertFalse(round.isActive());
    }

    @Test
    void noContainHeldRoundOpensWhenTheMatchupOrBuildDoesNotAllowIt() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, held().eligible(false).build());

        assertFalse(round.isActive());
    }

    @Test
    void noContainHeldRoundOpensUnderAThreat() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, THREAT, held().build());

        assertFalse(round.isActive());
    }

    @Test
    void theSoftCapRefusesAContainHeldRound() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, held().workers(SOFT_CAP).build());
        assertFalse(round.isActive());

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, held().workers(SOFT_CAP - 1).build());
        assertTrue(round.isActive());
    }

    @Test
    void theHardCapRefusesAContainHeldRound() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM,
                held().softCap(HARD_CAP + 10).workers(HARD_CAP).build());
        assertFalse(round.isActive());

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM,
                held().softCap(HARD_CAP + 10).workers(HARD_CAP - 1).build());
        assertTrue(round.isActive());
    }

    @Test
    void reachingTheSoftCapClosesAContainHeldRound() {
        DroneRound round = openContainHeldRound();
        PlanEvents.register(recorder());

        round.update(FRAME + 1, NO_ARMY, DRONES + 1, 0, WANTED, CALM,
                held().heldFrames(FRAME + 1 - HELD_AT).workers(SOFT_CAP).build());

        assertFalse(round.isActive());
        assertEquals(Collections.singletonList("CLOSE:CONTAIN_HELD:SOFT_CAP:" + (DRONES + 1)), reports);
    }

    @Test
    void reachingTheHardCapClosesAContainHeldRound() {
        DroneRound round = openContainHeldRound();
        PlanEvents.register(recorder());

        round.update(FRAME + 1, NO_ARMY, DRONES + 1, 0, WANTED, CALM,
                held().heldFrames(FRAME + 1 - HELD_AT).workers(HARD_CAP).softCap(HARD_CAP + 10).build());

        assertFalse(round.isActive());
        assertEquals(Collections.singletonList("CLOSE:CONTAIN_HELD:HARD_CAP:" + (DRONES + 1)), reports);
    }

    @Test
    void aContainHeldRoundClosesOnceItsDronesAreCounted() {
        DroneRound round = openContainHeldRound();

        round.update(FRAME + 1, NO_ARMY, DRONES + 2, 0, WANTED, CALM, heldAt(FRAME + 1));
        assertTrue(round.isActive());

        PlanEvents.register(recorder());
        round.update(FRAME + 2, NO_ARMY, DRONES + 3, 0, WANTED, CALM, heldAt(FRAME + 2));
        assertFalse(round.isActive());
        assertEquals(Collections.singletonList("CLOSE:CONTAIN_HELD:SIZE:" + (DRONES + 3)), reports);
    }

    @Test
    void aContainHeldRoundClosesWhenTheContainItOpenedOnEnds() {
        DroneRound round = openContainHeldRound();
        PlanEvents.register(recorder());

        round.update(FRAME + 1, NO_ARMY, DRONES, 0, WANTED, CALM,
                held().chainStartFrame(ContainHeldTimer.NO_CHAIN).heldFrames(0).build());

        assertFalse(round.isActive());
        assertEquals(Collections.singletonList("CLOSE:CONTAIN_HELD:CONTAIN_ENDED:" + DRONES), reports);
    }

    @Test
    void aContainHeldRoundClosesWhenANewChainReplacesTheOneItOpenedOn() {
        DroneRound round = openContainHeldRound();

        round.update(FRAME + 1, NO_ARMY, DRONES, 0, WANTED, CALM,
                held().chainStartFrame(FRAME + 1).heldFrames(0).build());

        assertFalse(round.isActive());
    }

    @Test
    void aThreatClosesAContainHeldRound() {
        DroneRound round = openContainHeldRound();
        PlanEvents.register(recorder());

        round.update(FRAME + 1, NO_ARMY, DRONES, 0, WANTED, THREAT, heldAt(FRAME + 1));

        assertFalse(round.isActive());
        assertEquals(Collections.singletonList("CLOSE:CONTAIN_HELD:THREAT:" + DRONES), reports);
    }

    @Test
    void aContainHeldRoundThatCannotFinishClosesAfterTheLongestRound() {
        DroneRound round = openContainHeldRound();

        round.update(FRAME + DroneRound.MAX_ROUND_FRAMES - 1, NO_ARMY, DRONES, 0, WANTED, CALM,
                heldAt(FRAME + DroneRound.MAX_ROUND_FRAMES - 1));
        assertTrue(round.isActive());

        PlanEvents.register(recorder());
        round.update(FRAME + DroneRound.MAX_ROUND_FRAMES, NO_ARMY, DRONES, 0, WANTED, CALM,
                heldAt(FRAME + DroneRound.MAX_ROUND_FRAMES));
        assertFalse(round.isActive());
        assertEquals(Collections.singletonList("CLOSE:CONTAIN_HELD:TIMEOUT:" + DRONES), reports);
    }

    @Test
    void aContainHeldCloseLeavesTheArmyMilestoneUnchanged() {
        DroneRound round = new DroneRound();
        round.update(FRAME, DroneRound.FIRST_ROUND_ARMY_UNITS - 1, DRONES, CAP, WANTED, CALM, held().build());
        assertEquals(DroneRound.OpenReason.CONTAIN_HELD, round.getReason());
        int milestone = round.getArmyMilestone();

        round.update(FRAME + 1, 20, DRONES + 3, CAP, WANTED, CALM, heldAt(FRAME + 1));

        assertFalse(round.isActive());
        assertEquals(milestone, round.getArmyMilestone());
    }

    @Test
    void anArmyMilestoneRoundCloseStillMovesTheMilestone() {
        DroneRound round = openRound();
        PlanEvents.register(recorder());

        round.update(FRAME + 1, 8, round.getDroneTarget(), CAP, WANTED, CALM, heldAt(FRAME + 1));

        assertEquals(8 + DroneRound.ARMY_UNITS_PER_ROUND, round.getArmyMilestone());
        assertEquals(Collections.singletonList("CLOSE:ARMY_MILESTONE:SIZE:" + round.getDroneTarget()), reports);
    }

    @Test
    void anOpenContainHeldRoundIsNotClosedByTheBuildsCapOrTheArmyMilestoneGates() {
        DroneRound round = openContainHeldRound();

        round.update(FRAME + 1, 40, DRONES + 1, 0, WANTED, CALM, heldAt(FRAME + 1));

        assertTrue(round.isActive());
        assertEquals(DroneRound.OpenReason.CONTAIN_HELD, round.getReason());
    }

    @Test
    void anotherContainHeldRoundWaitsForTheCooldown() {
        DroneRound round = openContainHeldRound();
        int close = FRAME + 10;
        round.update(close, NO_ARMY, DRONES + 3, 0, WANTED, CALM, heldAt(close));
        assertFalse(round.isActive());
        assertEquals(close, round.getLastContainHeldCloseFrame());

        int early = close + DroneRound.CONTAIN_HELD_COOLDOWN_FRAMES - 1;
        round.update(early, NO_ARMY, DRONES + 3, 0, WANTED, CALM, heldAt(early));
        assertFalse(round.isActive());

        int ready = close + DroneRound.CONTAIN_HELD_COOLDOWN_FRAMES;
        round.update(ready, NO_ARMY, DRONES + 3, 0, WANTED, CALM, heldAt(ready));
        assertTrue(round.isActive());
        assertEquals(DRONES + 6, round.getDroneTarget());
    }

    @Test
    void theCooldownAlsoFollowsAThreatClose() {
        DroneRound round = openContainHeldRound();
        round.update(FRAME + 1, NO_ARMY, DRONES, 0, WANTED, THREAT, heldAt(FRAME + 1));

        int early = FRAME + DroneRound.CONTAIN_HELD_COOLDOWN_FRAMES;
        round.update(early, NO_ARMY, DRONES, 0, WANTED, CALM, heldAt(early));

        assertFalse(round.isActive());
    }

    @Test
    void theArmyMilestoneRoundIsNotHeldBackByTheContainHeldCooldown() {
        DroneRound round = openContainHeldRound();
        round.update(FRAME + 1, NO_ARMY, DRONES + 3, 0, WANTED, CALM, heldAt(FRAME + 1));

        round.update(FRAME + 2, DroneRound.FIRST_ROUND_ARMY_UNITS, DRONES + 3, CAP, WANTED, CALM, heldAt(FRAME + 2));

        assertTrue(round.isActive());
        assertEquals(DroneRound.OpenReason.ARMY_MILESTONE, round.getReason());
    }

    @Test
    void everyRoundLogsOneOpenAndOneCloseWithAReason() {
        PlanEvents.register(recorder());
        DroneRound round = openContainHeldRound();
        round.update(FRAME + 1, NO_ARMY, DRONES + 1, 0, WANTED, CALM, heldAt(FRAME + 1));
        round.update(FRAME + 2, NO_ARMY, DRONES + 3, 0, WANTED, CALM, heldAt(FRAME + 2));
        round.update(FRAME + 3, NO_ARMY, DRONES + 3, 0, WANTED, CALM, heldAt(FRAME + 3));

        assertEquals(Arrays.asList("OPEN:CONTAIN_HELD:CONTAIN_HELD:3", "CLOSE:CONTAIN_HELD:SIZE:" + (DRONES + 3)),
                reports);
    }

    @Test
    void anArmyMilestoneRoundLogsItsOpenAndACloseReasonForEachExit() {
        PlanEvents.register(recorder());
        DroneRound round = openRound();
        round.update(FRAME + 1, DroneRound.FIRST_ROUND_ARMY_UNITS, DRONES, CAP, SATURATED, CALM);
        round.update(FRAME + 2, 20, DRONES, CAP, WANTED, CALM);
        round.update(FRAME + 3, 20, DRONES, CAP, WANTED, THREAT);
        round.update(FRAME + 4, 20, DRONES, 0, WANTED, CALM);

        assertEquals(Arrays.asList(
                "OPEN:ARMY_MILESTONE:ARMY_MILESTONE:" + DroneRound.DRONES_PER_ROUND,
                "CLOSE:ARMY_MILESTONE:HARD_CAP:" + DRONES,
                "OPEN:ARMY_MILESTONE:ARMY_MILESTONE:" + DroneRound.DRONES_PER_ROUND,
                "CLOSE:ARMY_MILESTONE:THREAT:" + DRONES), reports);
    }
}
