package unit.managed;

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
    void aWithdrawalHoldsForTheFullHoldFrames() {
        assertTrue(Lurker.withdrawHolds(500, 500));
        assertTrue(Lurker.withdrawHolds(500, 500 + 29));
        assertTrue(Lurker.withdrawHolds(500, 500 + Lurker.WITHDRAW_HOLD_FRAMES));
    }

    @Test
    void aWithdrawalEndsAfterTheHoldFrames() {
        assertFalse(Lurker.withdrawHolds(500, 500 + Lurker.WITHDRAW_HOLD_FRAMES + 1));
    }

    @Test
    void noWithdrawalHoldsWithoutOne() {
        assertFalse(Lurker.withdrawHolds(-1, 10));
    }

    @Test
    void anUnburrowedLurkerHoldingAWithdrawalDoesNotAnswerAnEnemyInRange() {
        assertFalse(Lurker.answersEnemyInRange(true, false, true));
    }

    @Test
    void aLurkerNotHoldingAWithdrawalAnswersAnEnemyInRange() {
        assertTrue(Lurker.answersEnemyInRange(true, false, false));
    }

    @Test
    void aBurrowedLurkerHoldingAWithdrawalStillAttacksAnEnemyInRange() {
        assertTrue(Lurker.answersEnemyInRange(true, true, true));
    }

    @Test
    void noEnemyInRangeIsNeverAnswered() {
        assertFalse(Lurker.answersEnemyInRange(false, true, false));
        assertFalse(Lurker.answersEnemyInRange(false, false, true));
    }

    @Test
    void aLurkerStandingInFixedFireUnburrowsWhenItsPointMovesALittle() {
        assertFalse(Lurker.staysBurrowed(10, false, true));
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
