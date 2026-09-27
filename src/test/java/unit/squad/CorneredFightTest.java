package unit.squad;

import org.junit.jupiter.api.Test;
import telemetry.RetreatRoute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

/**
 * The cornered fight rule: a retreating ground squad whose last plan was CORNERED turns to fight only once ENGAGE has
 * held over a fight hysteresis window, and then stays in FIGHT for one more.
 */
class CorneredFightTest {

    /**
     * The M0JDW06I flap: the sim reads ENGAGE on one Dragoon and RETREAT on Dragoon plus Zealot, alternating about
     * every 25 frames.
     */
    private static final int FLAP_PERIOD = 25;

    @Test
    void aSingleCorneredEngageDoesNotTurnTheSquadToFight() {
        Squad squad = corneredSquad(7471);

        assertFalse(squad.corneredEngagePersisted(corneredRead(squad, ENGAGE), 7472));
        assertFalse(squad.corneredEngagePersisted(corneredRead(squad, RETREAT), 7473));
        assertFalse(squad.corneredEngagePersisted(corneredRead(squad, ENGAGE), 7474));
    }

    @Test
    void aCorneredEngageHeldOverAFightHysteresisWindowTurnsTheSquadToFight() {
        Squad squad = corneredSquad(7471);
        int window = squad.getFightHysteresis().getFrames();

        for (int frame = 7472; frame < 7472 + window; frame++) {
            assertFalse(squad.corneredEngagePersisted(corneredRead(squad, ENGAGE), frame));
        }
        assertTrue(squad.corneredEngagePersisted(corneredRead(squad, ENGAGE), 7472 + window));
        assertTrue(squad.isRetreatLocked(7472 + window), "the run completes inside the retreat lock it breaks");
    }

    @Test
    void anEngageRetreatFlapNeverTurnsTheSquadToFight() {
        Squad squad = corneredSquad(7471);
        assertTrue(squad.getFightHysteresis().getFrames() > FLAP_PERIOD);

        for (int frame = 7472; frame < 8331; frame++) {
            boolean engage = (frame - 7472) / FLAP_PERIOD % 2 == 0;
            assertFalse(squad.corneredEngagePersisted(corneredRead(squad, engage ? ENGAGE : RETREAT), frame),
                    "frame " + frame);
        }
    }

    @Test
    void aSquadWithAWayHomeOrAContestedHomeNeverRunsTowardAFight() {
        for (RetreatRoute route : new RetreatRoute[]{RetreatRoute.HOME, RetreatRoute.DETOUR,
            RetreatRoute.HOME_CONTESTED, RetreatRoute.AWAY, RetreatRoute.NONE}) {
            Squad squad = corneredSquad(7471);
            squad.setRetreatRoute(route);
            int window = squad.getFightHysteresis().getFrames();

            for (int frame = 7472; frame <= 7472 + window; frame++) {
                assertFalse(squad.corneredEngagePersisted(corneredRead(squad, ENGAGE), frame), route.name());
            }
        }
    }

    @Test
    void aCorneredFightHoldsAgainstAMeasuredRetreatForOneWindow() {
        Squad squad = corneredSquad(7471);
        int window = squad.getFightHysteresis().getFrames();
        squad.setStatus(SquadStatus.FIGHT);
        squad.holdCorneredFight(7600);
        boolean lockHolds = SquadManager.fightLockHolds(true, RETREAT, true, 0.9, 1.25);

        assertFalse(lockHolds, "the fight lock alone releases on a measured RETREAT");
        assertTrue(SquadManager.fightHeld(squad, 7601, lockHolds));
        assertTrue(SquadManager.fightHeld(squad, 7600 + window - 1, lockHolds));
        assertFalse(SquadManager.fightHeld(squad, 7600 + window, lockHolds), "after the hold the sim decides again");
    }

    @Test
    void theCorneredHoldStartsTheNextEngageRunOver() {
        Squad squad = corneredSquad(7471);
        int window = squad.getFightHysteresis().getFrames();
        squad.corneredEngagePersisted(true, 7472);

        squad.holdCorneredFight(7472 + window);

        assertFalse(squad.corneredEngagePersisted(true, 7472 + window + 1));
        assertTrue(squad.corneredEngagePersisted(true, 7472 + 2 * window + 1));
    }

    private static Squad corneredSquad(int frame) {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.RETREAT);
        squad.setRetreatRoute(RetreatRoute.CORNERED);
        squad.startRetreatLock(frame);
        return squad;
    }

    private static boolean corneredRead(Squad squad, CombatSimulator.CombatResult result) {
        return SquadManager.corneredSquadFights(squad.getStatus(), squad.getRetreatRoute(), result);
    }
}
