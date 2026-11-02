package unit.managed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackCyclesTest {

    private static final int COOLDOWN = 30;

    private final AttackCycles cycles = new AttackCycles();
    private int lastStart = -1;

    private void frame(int frame, boolean start, boolean targetHeld, UnitRole role, boolean inThreat, int pool) {
        if (start) {
            lastStart = frame;
        }
        int cooldown = lastStart < 0 ? 0 : Math.max(0, COOLDOWN - (frame - lastStart));
        cycles.observe(frame, start, cooldown, targetHeld, role, inThreat, pool);
    }

    private void run(int from, int to, int[] starts, boolean targetHeld, UnitRole role) {
        for (int frame = from; frame <= to; frame++) {
            boolean start = false;
            for (int s : starts) {
                start |= s == frame;
            }
            frame(frame, start, targetHeld, role, false, 120);
        }
    }

    @Test
    void cyclesSumTheFramesBetweenConsecutiveAttackStarts() {
        run(100, 220, new int[] {100, 140, 175, 210}, true, UnitRole.FIGHT);

        assertEquals(3, cycles.getCycles());
        assertEquals(110, cycles.getCycleFrames());
    }

    @Test
    void readyFramesAreTheFramesAfterTheCooldownEndsBeforeTheNextAttack() {
        run(100, 220, new int[] {100, 140, 175, 210}, true, UnitRole.FIGHT);

        assertEquals(10 + 5 + 5, cycles.getReadyFrames());
    }

    @Test
    void aCycleWithoutATargetOnEveryFrameIsNotCounted() {
        run(100, 139, new int[] {100}, true, UnitRole.FIGHT);
        frame(140, true, true, UnitRole.FIGHT, false, 120);
        run(141, 150, new int[0], true, UnitRole.FIGHT);
        frame(151, false, false, UnitRole.FIGHT, false, 120);
        run(152, 200, new int[] {200}, true, UnitRole.FIGHT);

        assertEquals(1, cycles.getCycles());
        assertEquals(40, cycles.getCycleFrames());
        assertEquals(10, cycles.getReadyFrames());
    }

    @Test
    void aCycleAcrossARoleChangeIsNotCounted() {
        run(100, 120, new int[] {100}, true, UnitRole.FIGHT);
        run(121, 125, new int[0], true, UnitRole.RETREAT);
        run(126, 160, new int[] {160}, true, UnitRole.FIGHT);

        assertEquals(0, cycles.getCycles());
        assertEquals(0, cycles.getReadyFrames());
    }

    @Test
    void threatFramesAndDamageAreCountedOnlyWhileTheWeaponCoolsDown() {
        frame(100, true, true, UnitRole.FIGHT, true, 120);
        for (int frame = 101; frame <= 140; frame++) {
            boolean inThreat = frame < 110 || frame > 132;
            int pool = frame < 105 ? 120 : frame < 135 ? 111 : 100;
            frame(frame, false, true, UnitRole.FIGHT, inThreat, pool);
        }

        assertEquals(10, cycles.getCooldownFramesInThreat());
        assertEquals(9, cycles.getCooldownDamageTaken());
    }

    @Test
    void noAttackCountsNothing() {
        run(0, 100, new int[0], true, UnitRole.FIGHT);

        assertEquals(0, cycles.getCycles());
        assertEquals(0, cycles.getReadyFrames());
    }
}
