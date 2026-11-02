package unit.managed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackCyclesTest {

    private static final int COOLDOWN = 30;
    private static final int TARGET = 7;

    private final AttackCycles cycles = new AttackCycles();
    private int lastStart = -1;

    private void frame(int frame, boolean start, int targetId, boolean inReach, UnitRole role, boolean inThreat,
                       int pool) {
        if (start) {
            lastStart = frame;
        }
        int cooldown = lastStart < 0 ? 0 : Math.max(0, COOLDOWN - (frame - lastStart));
        cycles.observe(frame, start, cooldown, targetId, inReach, role, inThreat, pool);
    }

    private void run(int from, int to, int[] starts, int targetId, UnitRole role) {
        for (int frame = from; frame <= to; frame++) {
            boolean start = false;
            for (int s : starts) {
                start |= s == frame;
            }
            frame(frame, start, targetId, true, role, false, 120);
        }
    }

    @Test
    void cyclesSumTheFramesBetweenConsecutiveAttackStarts() {
        run(100, 220, new int[] {100, 140, 175, 210}, TARGET, UnitRole.FIGHT);

        assertEquals(3, cycles.getCycles());
        assertEquals(110, cycles.getCycleFrames());
        assertEquals(3, cycles.getInReachCycles());
        assertEquals(110, cycles.getInReachCycleFrames());
    }

    @Test
    void readyFramesAreTheFramesAfterTheCooldownEndsBeforeTheNextAttack() {
        run(100, 220, new int[] {100, 140, 175, 210}, TARGET, UnitRole.FIGHT);

        assertEquals(10 + 5 + 5, cycles.getReadyFrames());
    }

    @Test
    void cooldownFramesCountEveryFrameTheWeaponCoolsDown() {
        run(100, 220, new int[] {100, 140, 175, 210}, TARGET, UnitRole.FIGHT);

        assertEquals(4 * COOLDOWN - 19, cycles.getCooldownFrames());
    }

    @Test
    void aCycleWithoutATargetOnEveryFrameIsNotCounted() {
        run(100, 139, new int[] {100}, TARGET, UnitRole.FIGHT);
        frame(140, true, TARGET, true, UnitRole.FIGHT, false, 120);
        run(141, 150, new int[0], TARGET, UnitRole.FIGHT);
        frame(151, false, -1, false, UnitRole.FIGHT, false, 120);
        run(152, 200, new int[] {200}, TARGET, UnitRole.FIGHT);

        assertEquals(1, cycles.getCycles());
        assertEquals(40, cycles.getCycleFrames());
        assertEquals(10, cycles.getReadyFrames());
    }

    @Test
    void aCycleAcrossATargetChangeIsNotCounted() {
        run(100, 120, new int[] {100}, TARGET, UnitRole.FIGHT);
        run(121, 160, new int[] {160}, TARGET + 1, UnitRole.FIGHT);
        run(161, 200, new int[] {200}, TARGET + 1, UnitRole.FIGHT);

        assertEquals(1, cycles.getCycles());
        assertEquals(40, cycles.getCycleFrames());
    }

    @Test
    void aCycleAcrossARoleChangeIsNotCounted() {
        run(100, 120, new int[] {100}, TARGET, UnitRole.FIGHT);
        run(121, 125, new int[0], TARGET, UnitRole.RETREAT);
        run(126, 160, new int[] {160}, TARGET, UnitRole.FIGHT);

        assertEquals(0, cycles.getCycles());
        assertEquals(0, cycles.getReadyFrames());
    }

    @Test
    void aCycleIsInReachOnlyWhenTheTargetWasInRangeAtBothStarts() {
        frame(100, true, TARGET, true, UnitRole.FIGHT, false, 120);
        run(101, 139, new int[0], TARGET, UnitRole.FIGHT);
        frame(140, true, TARGET, false, UnitRole.FIGHT, false, 120);
        run(141, 179, new int[0], TARGET, UnitRole.FIGHT);
        frame(180, true, TARGET, true, UnitRole.FIGHT, false, 120);
        run(181, 209, new int[0], TARGET, UnitRole.FIGHT);
        frame(210, true, TARGET, true, UnitRole.FIGHT, false, 120);

        assertEquals(3, cycles.getCycles());
        assertEquals(1, cycles.getInReachCycles());
        assertEquals(30, cycles.getInReachCycleFrames());
    }

    @Test
    void threatFramesAndCooldownDamageAreCountedOnlyWhileTheWeaponCoolsDown() {
        frame(100, true, TARGET, true, UnitRole.FIGHT, true, 120);
        for (int frame = 101; frame <= 140; frame++) {
            boolean inThreat = frame < 110 || frame > 132;
            int pool = frame < 105 ? 120 : frame < 135 ? 111 : 100;
            frame(frame, false, TARGET, true, UnitRole.FIGHT, inThreat, pool);
        }

        assertEquals(10, cycles.getCooldownFramesInThreat());
        assertEquals(9, cycles.getCooldownDamageTaken());
        assertEquals(20, cycles.getHitPointsLost());
    }

    @Test
    void hitPointsLostSumTheDropsSoRegenerationDoesNotHideThem() {
        int[] pools = {120, 110, 112, 114, 104, 106};
        for (int i = 0; i < pools.length; i++) {
            frame(i, false, TARGET, true, UnitRole.FIGHT, false, pools[i]);
        }

        assertEquals(20, cycles.getHitPointsLost());
    }

    @Test
    void everyPlanOutcomeIsCounted() {
        cycles.notePlan(CooldownKite.Outcome.BACK_OFF);
        cycles.notePlan(CooldownKite.Outcome.BACK_OFF);
        cycles.notePlan(CooldownKite.Outcome.NO_CLEAR);
        cycles.notePlan(CooldownKite.Outcome.NO_THREAT);

        assertEquals(4, cycles.getKitePlans());
        assertEquals(2, cycles.getKiteBackoffs());
        assertEquals(1, cycles.getKiteNoClear());
        assertEquals(1, cycles.getKiteNoThreat());
    }

    @Test
    void noAttackCountsNothing() {
        run(0, 100, new int[0], TARGET, UnitRole.FIGHT);

        assertEquals(0, cycles.getCycles());
        assertEquals(0, cycles.getReadyFrames());
        assertEquals(0, cycles.getCooldownFrames());
    }
}
