package strategy.buildorder.zerg;

import bwapi.UnitType;
import info.UnitTypeCount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZergBaseTest {

    private static final int FRAMES = 30;

    private static final int NO_ENEMY_ZERGLINGS = 0;
    private static final int NO_LAIR = 0;
    private static final int NO_MINERALS = 0;

    private static int targetFor(UnitTypeCount count) {
        return ZergBase.zerglingTarget(count.get(UnitType.Zerg_Zergling), NO_ENEMY_ZERGLINGS, NO_LAIR, false, NO_MINERALS);
    }

    @Test
    void asksForTheBaseTargetWithNothingCommitted() {
        assertEquals(ZergBase.BASE_ZERGLING_TARGET,
                ZergBase.zerglingTarget(0, NO_ENEMY_ZERGLINGS, NO_LAIR, false, NO_MINERALS));
    }

    @Test
    void queueingZerglingPlansWalksTheTargetDownToZero() {
        UnitTypeCount count = new UnitTypeCount();
        int before = targetFor(count);

        for (int i = 0; i < ZergBase.BASE_ZERGLING_TARGET / 2; i++) {
            count.planUnit(UnitType.Zerg_Zergling);
        }

        assertEquals(ZergBase.BASE_ZERGLING_TARGET, before);
        assertEquals(0, targetFor(count));
    }

    @Test
    void doesNotKeepAskingWhileThePlannedZerglingsAreStillMorphing() {
        UnitTypeCount count = new UnitTypeCount();
        int plans = 0;

        for (int frame = 0; frame < FRAMES; frame++) {
            if (targetFor(count) > 0) {
                count.planUnit(UnitType.Zerg_Zergling);
                plans++;
            }
        }

        assertEquals(ZergBase.BASE_ZERGLING_TARGET / 2, plans);
    }

    @Test
    void raisesTheTargetForEnemyZerglingsALairAndSpeed() {
        assertEquals(ZergBase.BASE_ZERGLING_TARGET + 4 + 6 + 2, ZergBase.zerglingTarget(0, 4, 1, true, NO_MINERALS));
    }

    @Test
    void spendsMineralsAboveTheBankOnMoreZerglings() {
        assertEquals(ZergBase.BASE_ZERGLING_TARGET + 4,
                ZergBase.zerglingTarget(0, NO_ENEMY_ZERGLINGS, NO_LAIR, false, 500));
    }

    @Test
    void capsTheTarget() {
        assertEquals(ZergBase.MAX_ZERGLING_TARGET,
                ZergBase.zerglingTarget(0, NO_ENEMY_ZERGLINGS, NO_LAIR, false, 100000));
    }
}
