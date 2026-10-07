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
    void aWithdrawingLurkerInsideTheHitCellStillSkipsTheInRangeBurrow() {
        assertTrue(Lurker.stillInHitCell(0, false));
        assertTrue(Lurker.stillInHitCell(Lurker.HIT_CELL_RADIUS, false));
    }

    @Test
    void aWithdrawingLurkerPastTheHitCellBurrowsOnAnEnemyInRange() {
        assertFalse(Lurker.stillInHitCell(Lurker.HIT_CELL_RADIUS + 1, false));
        assertTrue(Lurker.answersEnemyInRange(true, false, false));
    }

    @Test
    void aWithdrawingLurkerPastTheHitCellButInsideFireIsStillInTheHitCell() {
        assertTrue(Lurker.stillInHitCell(Lurker.HIT_CELL_RADIUS + 300, true));
    }

    private static Lurker.BurrowCall call(boolean fireAware, boolean inFire, boolean inFixedFire, boolean hasSafe,
                                          boolean enemyInRange, boolean hurt, double safeDistance) {
        return Lurker.burrowCall(fireAware, inFire, inFixedFire, hasSafe, enemyInRange, hurt, safeDistance);
    }

    @Test
    void aLurkerRefusesToBurrowInsideFireWithNoEnemyInRangeNotHurtAndASafePointNear() {
        assertEquals(Lurker.BurrowCall.REFUSE, call(true, true, false, true, false, false, 40));
    }

    @Test
    void aLurkerRefusesToBurrowInsideFireWithNoEnemyInRangeNotHurtAndASafePointFar() {
        assertEquals(Lurker.BurrowCall.REFUSE, call(true, true, false, true, false, false, 300));
    }

    @Test
    void aLurkerBurrowsOutsideFireWhenTheRuleIsOn() {
        assertEquals(Lurker.BurrowCall.BURROW, call(true, false, false, true, true, true, 300));
    }

    @Test
    void aLurkerBurrowsInsideFireWhenTheRuleIsOff() {
        assertEquals(Lurker.BurrowCall.BURROW, call(false, true, false, true, true, true, 300));
    }

    @Test
    void aLurkerInsideFireWithNoSafePointBurrowsWhereItStands() {
        assertEquals(Lurker.BurrowCall.BURROW, call(true, true, false, false, false, false, 0));
    }

    @Test
    void aLurkerInsideOnlyAHitMarkBurrowsAndFiresWithAGroundEnemyInRange() {
        assertEquals(Lurker.BurrowCall.BURROW_ENEMY_IN_RANGE, call(true, true, false, true, true, false, 40));
    }

    @Test
    void aLurkerInsideFixedFireStillRefusesWithAGroundEnemyInRange() {
        assertEquals(Lurker.BurrowCall.REFUSE, call(true, true, true, true, true, false, 40));
    }

    @Test
    void aLurkerLosingHitPointsWithAFarSafePointBurrowsAndFires() {
        assertEquals(Lurker.BurrowCall.BURROW_LOSING_HP, call(true, true, false, true, false, true, 65));
    }

    @Test
    void aLurkerLosingHitPointsInsideFixedFireWithAFarSafePointBurrowsAndFires() {
        assertEquals(Lurker.BurrowCall.BURROW_LOSING_HP, call(true, true, true, true, true, true, 200));
    }

    @Test
    void aLurkerLosingHitPointsWithASafePointNearStillRefuses() {
        assertEquals(Lurker.BurrowCall.REFUSE,
                call(true, true, false, true, false, true, Lurker.SAFE_POINT_NEAR_DISTANCE));
    }

    @Test
    void aLurkerInFixedFireLosingHitPointsWithASafePointNearStillRefuses() {
        assertEquals(Lurker.BurrowCall.REFUSE, call(true, true, true, true, true, true, 30));
    }

    @Test
    void aHoldPointInsideFireIsReplacedByTheNearestSafePoint() {
        Position hold = new Position(100, 100);
        Position safe = new Position(300, 100);

        assertSame(safe, Lurker.holdTarget(true, true, hold, safe));
    }

    @Test
    void aHoldPointOutsideFireIsKept() {
        Position hold = new Position(100, 100);

        assertSame(hold, Lurker.holdTarget(true, false, hold, new Position(300, 100)));
    }

    @Test
    void aHoldPointInsideFireIsKeptWhenNoSafePointExists() {
        Position hold = new Position(100, 100);

        assertSame(hold, Lurker.holdTarget(true, true, hold, null));
    }

    @Test
    void aHoldPointInsideFireIsKeptWhenTheRuleIsOff() {
        Position hold = new Position(100, 100);

        assertSame(hold, Lurker.holdTarget(false, true, hold, new Position(300, 100)));
    }

    @Test
    void theFirstRefusalIsLoggedAndTheNextOnesWaitForTheInterval() {
        assertTrue(Lurker.logsRefusal(-Lurker.REFUSAL_LOG_FRAMES, 0));
        assertFalse(Lurker.logsRefusal(500, 500 + Lurker.REFUSAL_LOG_FRAMES - 1));
        assertTrue(Lurker.logsRefusal(500, 500 + Lurker.REFUSAL_LOG_FRAMES));
    }

    @Test
    void aSecondRefusalNearTheFirstBreaksTheLoop() {
        Position anchor = new Position(100, 100);

        assertEquals(Lurker.RefusalStep.BREAK,
                Lurker.refusalStep(anchor, 1000, new Position(105, 100), 1000 + Lurker.REFUSAL_LOOP_MIN_FRAMES));
    }

    @Test
    void aSecondRefusalFarFromTheFirstReanchors() {
        Position anchor = new Position(100, 100);

        assertEquals(Lurker.RefusalStep.REANCHOR,
                Lurker.refusalStep(anchor, 1000, new Position(100 + Lurker.REFUSAL_LOOP_DISTANCE, 100),
                        1000 + Lurker.REFUSAL_LOOP_MIN_FRAMES));
    }

    @Test
    void aStaleOrFirstRefusalReanchorsAndAnEarlyOneKeepsTheAnchor() {
        Position anchor = new Position(100, 100);

        assertEquals(Lurker.RefusalStep.REANCHOR,
                Lurker.refusalStep(anchor, 1000, anchor, 1000 + Lurker.REFUSAL_LOOP_STALE_FRAMES + 1));
        assertEquals(Lurker.RefusalStep.BREAK,
                Lurker.refusalStep(anchor, 1000, anchor, 1000 + Lurker.REFUSAL_LOOP_STALE_FRAMES));
        assertEquals(Lurker.RefusalStep.REANCHOR, Lurker.refusalStep(null, 0, anchor, 500));
        assertEquals(Lurker.RefusalStep.KEEP,
                Lurker.refusalStep(anchor, 1000, anchor, 1000 + Lurker.REFUSAL_LOOP_MIN_FRAMES - 1));
    }

    @Test
    void aRecentlyBurrowedLurkerKeepsItsPointForANearMove() {
        assertTrue(Lurker.keepsContainPoint(10, false, Lurker.CONTAIN_MOVE_DISTANCE - 1));
    }

    @Test
    void aSubstantiallyMovedPointOrAnElapsedHoldReleasesTheLurker() {
        assertFalse(Lurker.keepsContainPoint(10, false, Lurker.CONTAIN_MOVE_DISTANCE));
        assertFalse(Lurker.keepsContainPoint(Lurker.CONTAIN_LOCK_FRAMES, false, 100));
    }

    @Test
    void aLurkerLosingHitPointsHoldsItsPointLonger() {
        assertTrue(Lurker.keepsContainPoint(Lurker.CONTAIN_LOCK_FRAMES, true, 100));
        assertFalse(Lurker.keepsContainPoint(Lurker.CONTAIN_LOCK_FIRE_FRAMES, true, 100));
    }

    @Test
    void aFlippedBurrowCommandIsLogged() {
        assertTrue(Lurker.changesBurrowState(true, false));
        assertTrue(Lurker.changesBurrowState(false, true));
    }
}
