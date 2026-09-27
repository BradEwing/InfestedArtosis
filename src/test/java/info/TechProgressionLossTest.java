package info;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechProgressionLossTest {

    private static final boolean COMPLETED = true;

    private static final boolean MORPHING = false;

    /** Pool, Queen's Nest and a Hive standing on the Lair it morphed from. */
    private static TechProgression hiveTech() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);
        techProgression.setLair(true);
        techProgression.setQueensNest(true);
        techProgression.setHive(true);
        return techProgression;
    }

    private static void lose(TechProgression techProgression, UnitType destroyed, boolean completed,
                             int lairsCounted, int hivesCounted) {
        techProgression.loseLairOrHive(destroyed,
                TechProgression.standingAfterLoss(UnitType.Zerg_Lair, lairsCounted, destroyed, completed),
                TechProgression.standingAfterLoss(UnitType.Zerg_Hive, hivesCounted, destroyed, completed));
    }

    @Test
    void losingTheOnlyHiveLosesTheLairTechSoTheLairIsReplannedBeforeTheHive() {
        TechProgression techProgression = hiveTech();

        lose(techProgression, UnitType.Zerg_Hive, COMPLETED, 0, 1);

        assertFalse(techProgression.isHive());
        assertFalse(techProgression.isLair());
        assertFalse(techProgression.canPlanHive());
        assertTrue(techProgression.canPlanLair());
    }

    @Test
    void losingAHiveStillMorphingLosesTheLairItMorphedFrom() {
        TechProgression techProgression = hiveTech();
        techProgression.setHive(false);
        techProgression.setPlannedHive(true);

        lose(techProgression, UnitType.Zerg_Hive, MORPHING, 1, 0);

        assertFalse(techProgression.isLair());
        assertFalse(techProgression.isPlannedHive());
        assertTrue(techProgression.canPlanLair());
    }

    @Test
    void losingAHiveWithAnotherLairStandingKeepsTheLairTech() {
        TechProgression techProgression = hiveTech();

        lose(techProgression, UnitType.Zerg_Hive, COMPLETED, 1, 1);

        assertTrue(techProgression.isLair());
        assertFalse(techProgression.isHive());
        assertTrue(techProgression.canPlanHive());
    }

    @Test
    void losingOneOfTwoHivesKeepsBoth() {
        TechProgression techProgression = hiveTech();

        lose(techProgression, UnitType.Zerg_Hive, COMPLETED, 0, 2);

        assertTrue(techProgression.isLair());
        assertTrue(techProgression.isHive());
    }

    @Test
    void losingTheOnlyLairLosesTheLairTech() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);
        techProgression.setLair(true);

        lose(techProgression, UnitType.Zerg_Lair, COMPLETED, 1, 0);

        assertFalse(techProgression.isLair());
        assertTrue(techProgression.canPlanLair());
    }

    @Test
    void losingALairWhileAHiveStandsKeepsTheLairTech() {
        TechProgression techProgression = hiveTech();

        lose(techProgression, UnitType.Zerg_Lair, COMPLETED, 1, 1);

        assertTrue(techProgression.isLair());
        assertTrue(techProgression.isHive());
    }

    @Test
    void aStructureDyingMidMorphIsCountedAsWhatItMorphsFrom() {
        assertEquals(1, TechProgression.standingAfterLoss(UnitType.Zerg_Lair, 1, UnitType.Zerg_Lair, MORPHING));
        assertEquals(0, TechProgression.standingAfterLoss(UnitType.Zerg_Lair, 1, UnitType.Zerg_Hive, MORPHING));
        assertEquals(1, TechProgression.standingAfterLoss(UnitType.Zerg_Hive, 1, UnitType.Zerg_Hive, MORPHING));
        assertEquals(0, TechProgression.standingAfterLoss(UnitType.Zerg_Hive, 1, UnitType.Zerg_Hive, COMPLETED));
        assertEquals(0, TechProgression.standingAfterLoss(UnitType.Zerg_Hive, 0, UnitType.Zerg_Hive, COMPLETED));
    }

    @Test
    void otherStructuresLeaveTheLairAndHiveTechAlone() {
        TechProgression techProgression = hiveTech();

        techProgression.loseLairOrHive(UnitType.Zerg_Spire, 0, 0);

        assertTrue(techProgression.isLair());
        assertTrue(techProgression.isHive());
    }
}
