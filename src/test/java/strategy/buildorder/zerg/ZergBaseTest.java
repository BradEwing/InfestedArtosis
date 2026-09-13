package strategy.buildorder.zerg;

import bwapi.UnitType;
import info.UnitTypeCount;
import org.junit.jupiter.api.Test;
import util.Filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZergBaseTest {

    private static final int FRAMES = 30;

    private static final int NO_SPIRES = 0;
    private static final int NO_AIR_UNITS = 0;

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

    @Test
    void anObservedSpireAsksEveryBaseForASpore() {
        assertEquals(ZergBase.AIR_THREAT_SPORES, ZergBase.sporeTarget(1, NO_AIR_UNITS));
        assertTrue(ZergBase.AIR_THREAT_SPORES > 0);
    }

    @Test
    void anObservedArmedFlyerAsksEveryBaseForASporeWithoutAScoutedSpire() {
        assertEquals(ZergBase.AIR_THREAT_SPORES, ZergBase.sporeTarget(NO_SPIRES, 3));
    }

    @Test
    void noAirTechObservedAsksForNoSpore() {
        assertEquals(0, ZergBase.sporeTarget(NO_SPIRES, NO_AIR_UNITS));
    }

    /**
     * The flyer term is fed by the armed flyer filter, so the Overlords every Zerg opponent flies
     * do not raise the target on their own while the Mutalisks they precede do.
     */
    @Test
    void theFlyerTermCountsMutalisksButNotOverlords() {
        assertFalse(Filter.isAirCombatUnit(UnitType.Zerg_Overlord));
        assertTrue(Filter.isAirCombatUnit(UnitType.Zerg_Mutalisk));
    }
}
