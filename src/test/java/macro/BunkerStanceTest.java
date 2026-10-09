package macro;

import macro.BunkerStance.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.BunkerAdvanceEvent;
import telemetry.BunkerAttackEvent;
import telemetry.BunkerLossEvent;
import telemetry.BunkerSink;
import telemetry.BunkerStanceEvent;
import telemetry.BunkerTelemetry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerStanceTest {

    private static final int FRAME = 6000;
    private static final int DRONES = 14;
    private static final int WORKERS = 14;
    private static final int SOFT_CAP = 30;
    private static final int HARD_CAP = 40;
    private static final int NO_ARMY = 0;
    private static final boolean WANTED = true;
    private static final boolean CALM = false;
    private static final boolean THREAT = true;

    private final List<BunkerStanceEvent> events = new ArrayList<>();

    @AfterEach
    void clearSink() {
        BunkerTelemetry.clear();
    }

    private void recordStances() {
        BunkerTelemetry.register(new BunkerSink() {
            @Override
            public void onAdvance(BunkerAdvanceEvent event) {
            }

            @Override
            public void onHold(int frame, String event, String reason) {
            }

            
            public void onLoss(BunkerLossEvent event) {
            }

            
            public void onAttack(BunkerAttackEvent event) {
            }

            @Override
            public void onStance(BunkerStanceEvent event) {
                events.add(event);
            }
        });
    }

    private static DroneRound.ContainHeld stance(int stanceId, int workers) {
        return DroneRound.ContainHeld.builder()
                .workers(workers)
                .softCap(SOFT_CAP)
                .hardCap(HARD_CAP)
                .bunkerStanceId(stanceId)
                .build();
    }

    private static DroneRound openRound() {
        DroneRound round = new DroneRound();
        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(1, WORKERS));
        return round;
    }

    @Test
    void aStanceStandsWhileABunkerHoldsNothingIsBrokenTheArmyIsNotAttackingAndTheBuildAllowsIt() {
        assertEquals(Status.ACTIVE, BunkerStance.evaluate(true, true, false, false, true));
    }

    @Test
    void eachConditionAloneEndsTheStance() {
        assertEquals(Status.SWITCH_OFF, BunkerStance.evaluate(false, true, false, false, true));
        assertEquals(Status.NOT_HELD, BunkerStance.evaluate(true, false, false, false, true));
        assertEquals(Status.BROKEN, BunkerStance.evaluate(true, true, true, false, true));
        assertEquals(Status.ATTACKING, BunkerStance.evaluate(true, true, false, true, true));
        assertEquals(Status.BUILD, BunkerStance.evaluate(true, true, false, false, false));
    }

    @Test
    void aBrokenBunkerEndsTheStanceEvenWhileAnotherHolds() {
        assertEquals(Status.BROKEN, BunkerStance.evaluate(true, true, true, true, false));
    }

    @Test
    void aStanceOpensARoundOfTwoDrones() {
        DroneRound round = openRound();

        assertTrue(round.isActive());
        assertEquals(DroneRound.OpenReason.BUNKER_STANCE, round.getReason());
        assertEquals(DroneRound.BUNKER_STANCE_ROUND_SIZE, round.getRoundSize());
        assertEquals(DRONES + 2, round.getDroneTarget());
    }

    @Test
    void noRoundOpensWithoutAStance() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(0, WORKERS));

        assertFalse(round.isActive());
    }

    @Test
    void noRoundOpensUnderAThreat() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, THREAT, stance(1, WORKERS));

        assertFalse(round.isActive());
    }

    @Test
    void theRoundIsCutToTheRoomUnderTheCapsAndNeverOpensAtThem() {
        DroneRound cut = new DroneRound();
        cut.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(1, SOFT_CAP - 1));
        DroneRound full = new DroneRound();
        full.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(1, SOFT_CAP));

        assertEquals(1, cut.getRoundSize());
        assertFalse(full.isActive());
    }

    @Test
    void theBuildsDroneCapDoesNotBoundTheRound() {
        DroneRound round = new DroneRound();

        round.update(FRAME, NO_ARMY, DRONES, DRONES, WANTED, CALM, stance(1, WORKERS));

        assertTrue(round.isActive());
    }

    @Test
    void theRoundClosesWhenTheStanceStopsStanding() {
        DroneRound round = openRound();

        round.update(FRAME + 10, NO_ARMY, DRONES, 0, WANTED, CALM, stance(0, WORKERS));

        assertFalse(round.isActive());
        assertEquals(DroneRound.CloseReason.BUNKER_STANCE_ENDED, round.getLastCloseReason());
    }

    @Test
    void theRoundClosesOnItsSizeThreatAndTimeout() {
        DroneRound sized = openRound();
        sized.update(FRAME + 10, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(1, WORKERS + 2));
        assertEquals(DroneRound.CloseReason.SIZE, sized.getLastCloseReason());

        DroneRound threatened = openRound();
        threatened.update(FRAME + 10, NO_ARMY, DRONES, 0, WANTED, THREAT, stance(1, WORKERS));
        assertEquals(DroneRound.CloseReason.THREAT, threatened.getLastCloseReason());

        DroneRound late = openRound();
        late.update(FRAME + DroneRound.MAX_ROUND_FRAMES, NO_ARMY, DRONES, 0, WANTED, CALM, stance(1, WORKERS));
        assertEquals(DroneRound.CloseReason.TIMEOUT, late.getLastCloseReason());
    }

    @Test
    void aStanceOpensOneRoundAndNoMoreHoweverLongItStands() {
        DroneRound round = openRound();
        round.update(FRAME + 10, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(1, WORKERS + 2));

        round.update(FRAME + 100000, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(1, WORKERS + 2));

        assertFalse(round.isActive());
    }

    @Test
    void aNewStanceOpensItsOwnRound() {
        DroneRound round = openRound();
        round.update(FRAME + 10, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(1, WORKERS + 2));

        round.update(FRAME + 20, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(2, WORKERS + 2));

        assertTrue(round.isActive());
        assertEquals(2, round.getLastBunkerStanceRoundId());
    }

    @Test
    void aStanceRoundNeverMovesTheArmyMilestone() {
        DroneRound round = openRound();
        int milestone = round.getArmyMilestone();

        round.update(FRAME + 10, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(1, WORKERS + 2));

        assertEquals(milestone, round.getArmyMilestone());
    }

    @Test
    void aStanceReportsItsStartItsRoundAndItsEndWithTheDronesPlannedAndMade() {
        recordStances();
        BunkerStance stance = new BunkerStance();
        DroneRound round = new DroneRound();

        stance.setStatus(Status.ACTIVE, true);
        assertEquals(1, stance.getStanceId());
        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS));
        stance.record(FRAME, round, DRONES, WORKERS);
        stance.onRoundDroneMade();
        stance.onRoundDroneMade();
        round.update(FRAME + 100, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS + 2));
        stance.record(FRAME + 100, round, DRONES + 2, WORKERS + 2);
        stance.setStatus(Status.ATTACKING, true);
        round.update(FRAME + 200, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS + 2));
        stance.record(FRAME + 200, round, DRONES + 2, WORKERS + 2);

        assertEquals(4, events.size());
        assertEquals("STANCE_START", events.get(0).getEvent());
        assertEquals(1, events.get(0).getStanceId());
        assertEquals("ROUND_OPEN", events.get(1).getEvent());
        assertEquals(2, events.get(1).getExtraPlanned());
        assertEquals("ROUND_CLOSE", events.get(2).getEvent());
        assertEquals("SIZE", events.get(2).getReason());
        assertEquals(2, events.get(2).getExtraMade());
        assertEquals("STANCE_END", events.get(3).getEvent());
        assertEquals("ATTACKING", events.get(3).getReason());
        assertEquals(2, events.get(3).getExtraPlanned());
        assertEquals(2, events.get(3).getExtraMade());
    }

    @Test
    void aStanceThatEndsWithItsHoldAndStandsAgainIsANewStance() {
        recordStances();
        BunkerStance stance = new BunkerStance();
        DroneRound round = new DroneRound();

        stance.setStatus(Status.ACTIVE, true);
        stance.record(FRAME, round, DRONES, WORKERS);
        stance.setStatus(Status.NOT_HELD, false);
        stance.record(FRAME + 10, round, DRONES, WORKERS);
        stance.setStatus(Status.ACTIVE, true);
        stance.record(FRAME + 20, round, DRONES, WORKERS);

        assertEquals(3, events.size());
        assertEquals(2, events.get(2).getStanceId());
        assertEquals(0, events.get(2).getExtraPlanned());
    }

    @Test
    void aStanceThatEndsBecauseTheArmyAttacksAndStandsAgainInTheSameHoldKeepsItsIdAndOpensNoSecondRound() {
        BunkerStance stance = new BunkerStance();
        DroneRound round = new DroneRound();

        stance.setStatus(Status.ACTIVE, true);
        int firstId = stance.getStanceId();
        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(firstId, WORKERS));
        round.update(FRAME + 10, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(firstId, WORKERS + 2));
        stance.setStatus(Status.ATTACKING, true);
        stance.setStatus(Status.ACTIVE, true);
        round.update(FRAME + 20, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS + 2));

        assertEquals(firstId, stance.getStanceId());
        assertFalse(round.isActive());
    }

    @Test
    void aHoldThatEndsAndAnotherThatStartsGetOneRoundEach() {
        BunkerStance stance = new BunkerStance();
        DroneRound round = new DroneRound();

        stance.setStatus(Status.ACTIVE, true);
        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS));
        round.update(FRAME + 10, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS + 2));
        stance.setStatus(Status.NOT_HELD, false);
        stance.setStatus(Status.ACTIVE, true);
        round.update(FRAME + 20, NO_ARMY, DRONES + 2, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS + 2));

        assertEquals(2, stance.getStanceId());
        assertTrue(round.isActive());
    }

    @Test
    void onlyDronesMadeWhileTheStanceRoundIsOpenCountAsMade() {
        recordStances();
        BunkerStance stance = new BunkerStance();
        DroneRound round = new DroneRound();

        stance.setStatus(Status.ACTIVE, true);
        stance.onRoundDroneMade();
        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS));
        stance.record(FRAME, round, DRONES, WORKERS);
        stance.onRoundDroneMade();
        round.update(FRAME + 100, NO_ARMY, DRONES + 5, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS + 5));
        stance.record(FRAME + 100, round, DRONES + 5, WORKERS + 5);
        stance.onRoundDroneMade();

        BunkerStanceEvent close = events.get(events.size() - 1);
        assertEquals("ROUND_CLOSE", close.getEvent());
        assertEquals(1, close.getExtraMade());
    }

    @Test
    void anOpenRoundAndStanceAreFlushedWhenTheGameEnds() {
        recordStances();
        BunkerStance stance = new BunkerStance();
        DroneRound round = new DroneRound();

        stance.setStatus(Status.ACTIVE, true);
        round.update(FRAME, NO_ARMY, DRONES, 0, WANTED, CALM, stance(stance.getStanceId(), WORKERS));
        stance.record(FRAME, round, DRONES, WORKERS);
        stance.onRoundDroneMade();
        stance.flush(FRAME + 500);

        assertEquals(4, events.size());
        assertEquals("ROUND_CLOSE", events.get(2).getEvent());
        assertEquals("GAME_END", events.get(2).getReason());
        assertEquals(1, events.get(2).getExtraMade());
        assertEquals("STANCE_END", events.get(3).getEvent());
        assertEquals("GAME_END", events.get(3).getReason());
        assertEquals(1, events.get(3).getExtraMade());
    }

    @Test
    void aFlushWithNothingOpenWritesNothing() {
        recordStances();

        new BunkerStance().flush(FRAME);

        assertTrue(events.isEmpty());
    }
}
