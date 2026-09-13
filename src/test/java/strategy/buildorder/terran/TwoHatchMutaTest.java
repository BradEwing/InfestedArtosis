package strategy.buildorder.terran;

import bwapi.UnitType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.AdvancedUnitEligibility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoHatchMutaTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final int PLANNED_MUTALISKS = 7;

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

    @Test
    void derivesTheMutaliskWithASpireAndTheGathererFloor() {
        assertTrue(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 0, 9, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskBelowTheGathererFloor() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 0, 9, GATHERER_FLOOR - 1));
    }

    @Test
    void withholdsTheMutaliskWithoutASpire() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(new TechProgression(), 0, 9, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskOnceTheCountIsMet() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 9, 9, GATHERER_FLOOR));
    }

    /**
     * Game LBIDH0GO frame 8,233: the seventh Mutalisk plan was queued with none alive, and Flyer
     * Attacks was queued on the same frame.
     */
    @Test
    void withholdsFlyerAttacksWhileTheMutalisksAreOnlyPlanned() {
        UnitTypeCount count = mutalisks(PLANNED_MUTALISKS, 0);

        assertTrue(count.get(UnitType.Zerg_Mutalisk) > 6);
        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(withSpire(), count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void withholdsFlyerAttacksWithSixLivingMutalisksAndMorePlanned() {
        UnitTypeCount count = mutalisks(PLANNED_MUTALISKS, 6);

        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(withSpire(), count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void plansFlyerAttacksWithSevenLivingMutalisks() {
        UnitTypeCount count = mutalisks(0, 7);

        assertTrue(TwoHatchMuta.shouldPlanFlyerAttack(withSpire(), count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void withholdsFlyerAttacksWithoutASpire() {
        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(new TechProgression(), 7));
    }

    /**
     * Game LBIDH0GO frame 12,830: level 1 finished and level 2 was queued the same frame with no
     * Mutalisk alive.
     */
    @Test
    void withholdsTheSecondLevelOnTheFrameTheFirstCompletesWithoutLivingMutalisks() {
        TechProgression techProgression = withSpire();
        techProgression.setPlannedFlyerAttack(true);
        UnitTypeCount count = mutalisks(PLANNED_MUTALISKS, 3);

        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(techProgression, count.livingCount(UnitType.Zerg_Mutalisk)));

        techProgression.setFlyerAttack(1);
        techProgression.setPlannedFlyerAttack(false);

        assertTrue(techProgression.canPlanFlyerAttack());
        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(techProgression, count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void plansTheSecondLevelOnceTheFirstCompletesWithSevenLivingMutalisks() {
        TechProgression techProgression = withSpire();
        techProgression.setFlyerAttack(1);

        assertTrue(TwoHatchMuta.shouldPlanFlyerAttack(techProgression, 7));
    }

    private static UnitTypeCount mutalisks(int planned, int living) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < planned; i++) {
            count.planUnit(UnitType.Zerg_Mutalisk);
        }
        for (int i = 0; i < living; i++) {
            count.addUnit(UnitType.Zerg_Mutalisk);
        }
        return count;
    }

    @Test
    void derivesTheOverlordWhileSupplyIsTight() {
        assertTrue(TwoHatchMuta.shouldPlanOverlord(2, 3, false));
    }

    @Test
    void withholdsTheOverlordWhileSupplyIsExcess() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(2, 3, true));
    }

    @Test
    void withholdsTheOverlordOnceTheCountIsMet() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(2, 4, false));
    }

    @Test
    void withholdsTheOverlordBelowTwoSpires() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(1, 3, false));
    }
}
