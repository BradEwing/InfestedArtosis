package unit.managed;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LurkerTest {

    private static final int RANGE = 192;

    private static final class Enemy {
        private final String name;
        private final double distance;
        private final boolean flying;
        private final boolean detected;

        Enemy(String name, double distance, boolean flying, boolean detected) {
            this.name = name;
            this.distance = distance;
            this.flying = flying;
            this.detected = detected;
        }

        boolean canBeHit() {
            return Lurker.isGroundTarget(flying, detected);
        }
    }

    private static Enemy pick(Enemy... enemies) {
        return Lurker.closestInRange(Arrays.asList(enemies), Enemy::canBeHit, enemy -> enemy.distance, RANGE);
    }

    @Test
    void anAirUnitIsNeverTheInRangePick() {
        Enemy vessel = new Enemy("vessel", 40, true, true);
        Enemy vulture = new Enemy("vulture", 150, false, true);

        assertSame(vulture, pick(vessel, vulture));
    }

    @Test
    void anAirUnitAloneLeavesNoPick() {
        assertNull(pick(new Enemy("vessel", 40, true, true)));
    }

    @Test
    void anUndetectedGroundUnitIsNotAPick() {
        assertNull(pick(new Enemy("ghost", 40, false, false)));
    }

    @Test
    void aGroundEnemyBeyondRangeIsNotAPick() {
        assertNull(pick(new Enemy("vulture", RANGE + 1, false, true)));
    }

    @Test
    void aFightingLurkerPicksAGroundEnemyInRangeThatIsNotItsAssignedTarget() {
        Enemy assignedVulture = new Enemy("vulture", 300, false, true);
        Enemy marine = new Enemy("marine", 120, false, true);

        Enemy picked = pick(assignedVulture, marine);

        assertSame(marine, picked);
        assertTrue(picked != assignedVulture);
    }

    @Test
    void theClosestGroundEnemyInRangeWins() {
        Enemy near = new Enemy("near", 90, false, true);
        Enemy far = new Enemy("far", 160, false, true);

        assertSame(near, pick(far, near));
    }

    @Test
    void aSmallContainPointMoveOutsideFixedFireKeepsTheLurkerBurrowed() {
        assertTrue(Lurker.staysBurrowed(Lurker.KEEP_BURROWED_DISTANCE - 1, false, false));
    }

    @Test
    void aContainPointMoveOfSixtyFourPixelsOrMoreUnburrows() {
        assertFalse(Lurker.staysBurrowed(Lurker.KEEP_BURROWED_DISTANCE, false, false));
        assertFalse(Lurker.staysBurrowed(200, false, false));
    }

    @Test
    void aContainPointInsideFixedFireUnburrowsEvenWhenClose() {
        assertFalse(Lurker.staysBurrowed(10, true, false));
    }

    @Test
    void aWithdrawalHoldsForTheContainPointItWasMadeFrom() {
        Position point = new Position(100, 100);

        assertTrue(Lurker.withdrawHolds(point, new Position(100, 100), 500, 500));
        assertTrue(Lurker.withdrawHolds(point, point, 500, 500 + Lurker.WITHDRAW_HOLD_FRAMES));
    }

    @Test
    void aWithdrawalEndsWhenTheContainPointChanges() {
        assertFalse(Lurker.withdrawHolds(new Position(100, 100), new Position(140, 100), 500, 510));
    }

    @Test
    void aWithdrawalEndsAfterTheHoldFrames() {
        Position point = new Position(100, 100);

        assertFalse(Lurker.withdrawHolds(point, point, 500, 500 + Lurker.WITHDRAW_HOLD_FRAMES + 1));
    }

    @Test
    void noWithdrawalHoldsWithoutOne() {
        Position point = new Position(100, 100);

        assertFalse(Lurker.withdrawHolds(null, point, -1, 10));
    }


    @Test
    void aLurkerStandingInFixedFireUnburrowsWhenItsPointMovesALittle() {
        assertFalse(Lurker.staysBurrowed(10, false, true));
    }

    @Test
    void aFreshContainPointOnTheHitFrameSkipsTheWithdrawalHold() {
        assertFalse(Lurker.recordsWithdrawal(500, 500));
        assertTrue(Lurker.recordsWithdrawal(499, 500));
        assertTrue(Lurker.recordsWithdrawal(-1, 500));
    }

    @Test
    void aFightingLurkerKeepsItsAssignedBuildingWhileItIsInRange() {
        assertEquals("bunker", Lurker.pickFightTarget("bunker", true, "marine"));
    }

    @Test
    void aFightingLurkerSwitchesToAGroundEnemyInRangeWhenItsTargetIsOutOfRange() {
        assertEquals("marine", Lurker.pickFightTarget("vulture", false, "marine"));
    }

    @Test
    void aFightingLurkerKeepsItsTargetWhenNothingIsInRange() {
        assertEquals("vulture", Lurker.pickFightTarget("vulture", false, null));
    }

    @Test
    void aFightingLurkerBurrowsOnAnyInRangeGroundEnemyOrItsInRangeTarget() {
        assertTrue(Lurker.burrowsInFight(false, true, true));
        assertFalse(Lurker.burrowsInFight(false, true, false));
        assertFalse(Lurker.burrowsInFight(true, true, true));
        assertFalse(Lurker.burrowsInFight(false, false, true));
    }

    @Test
    void onlyABurrowedContainingLurkerThatCanUnburrowWithdraws() {
        assertTrue(Lurker.canWithdraw(UnitRole.CONTAIN, true, true));
        assertFalse(Lurker.canWithdraw(UnitRole.CONTAIN, false, true));
        assertFalse(Lurker.canWithdraw(UnitRole.CONTAIN, true, false));
        assertFalse(Lurker.canWithdraw(UnitRole.FIGHT, true, true));
        assertFalse(Lurker.canWithdraw(UnitRole.RETREAT, true, true));
    }

    @Test
    void aLurkerWithAnEnemyInReachIsNotHitOutOfRange() {
        assertFalse(ManagedUnit.isOutrangedHit(100, 80, UnitRole.CONTAIN, true));
    }

    @Test
    void theFirstBurrowCommandIsLogged() {
        assertTrue(Lurker.changesBurrowState(null, true));
        assertTrue(Lurker.changesBurrowState(null, false));
    }

    @Test
    void aRepeatedBurrowCommandIsNotLogged() {
        assertFalse(Lurker.changesBurrowState(true, true));
        assertFalse(Lurker.changesBurrowState(false, false));
    }

    @Test
    void aFlippedBurrowCommandIsLogged() {
        assertTrue(Lurker.changesBurrowState(true, false));
        assertTrue(Lurker.changesBurrowState(false, true));
    }
}
