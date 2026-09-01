package macro;

import bwapi.UnitType;
import info.TechProgression;
import macro.plan.PlanBlocker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedUnitEligibilityTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

    @Test
    void aMutaliskIsEligibleWithACompleteSpireAndTheGathererFloor() {
        assertEquals(PlanBlocker.NONE,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Mutalisk, withSpire(), GATHERER_FLOOR));
    }

    @Test
    void aMutaliskIsHeldByTheGathererFloorWhileTheSpireIsComplete() {
        assertEquals(PlanBlocker.INSUFFICIENT_GATHERERS,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Mutalisk, withSpire(), GATHERER_FLOOR - 1));
    }

    @Test
    void theGathererFloorIsExact() {
        for (int gatherers = 0; gatherers < GATHERER_FLOOR; gatherers++) {
            assertEquals(PlanBlocker.INSUFFICIENT_GATHERERS,
                    AdvancedUnitEligibility.blocker(UnitType.Zerg_Mutalisk, withSpire(), gatherers),
                    gatherers + " gatherers must hold the mutalisk");
        }
        assertEquals(PlanBlocker.NONE,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Mutalisk, withSpire(), GATHERER_FLOOR + 10));
    }

    @Test
    void missingTechIsReportedBeforeTheGathererFloor() {
        TechProgression techProgression = new TechProgression();

        assertEquals(PlanBlocker.TECH_MISSING,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Mutalisk, techProgression, GATHERER_FLOOR));
        assertEquals(PlanBlocker.TECH_MISSING,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Mutalisk, techProgression, 0));
    }

    @Test
    void aPlannedTechBuildingCountsAsTech() {
        TechProgression techProgression = new TechProgression();
        techProgression.setPlannedSpire(true);

        assertEquals(PlanBlocker.NONE,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Scourge, techProgression, GATHERER_FLOOR));
    }

    @Test
    void eachAdvancedUnitReadsItsOwnTechFlag() {
        TechProgression den = new TechProgression();
        den.setHydraliskDen(true);
        TechProgression lurker = new TechProgression();
        lurker.setLurker(true);
        TechProgression cavern = new TechProgression();
        cavern.setUltraliskCavern(true);
        TechProgression mound = new TechProgression();
        mound.setDefilerMound(true);

        assertTrue(AdvancedUnitEligibility.hasTech(UnitType.Zerg_Hydralisk, den));
        assertTrue(AdvancedUnitEligibility.hasTech(UnitType.Zerg_Lurker, lurker));
        assertTrue(AdvancedUnitEligibility.hasTech(UnitType.Zerg_Mutalisk, withSpire()));
        assertTrue(AdvancedUnitEligibility.hasTech(UnitType.Zerg_Scourge, withSpire()));
        assertTrue(AdvancedUnitEligibility.hasTech(UnitType.Zerg_Ultralisk, cavern));
        assertTrue(AdvancedUnitEligibility.hasTech(UnitType.Zerg_Defiler, mound));

        assertFalse(AdvancedUnitEligibility.hasTech(UnitType.Zerg_Hydralisk, withSpire()));
        assertFalse(AdvancedUnitEligibility.hasTech(UnitType.Zerg_Mutalisk, den));
    }

    @Test
    void aUnitNoTechBuildingUnlocksIsNeverEligible() {
        TechProgression techProgression = withSpire();
        techProgression.setSpawningPool(true);

        assertEquals(PlanBlocker.TECH_MISSING,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Zergling, techProgression, GATHERER_FLOOR));
    }
}
