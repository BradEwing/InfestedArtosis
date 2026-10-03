package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.UnitTypeCount;
import org.junit.jupiter.api.Test;
import strategy.buildorder.LarvaBoundMacroHatchery;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoHatchHydraTerranTest {

    @Test
    void isATerranBuildThatPlaysOnlyTerran() {
        TwoHatchHydraTerran build = new TwoHatchHydraTerran();

        assertTrue(build instanceof TerranBase);
        assertEquals("2HatchHydraZvT", build.getName());
        assertTrue(build.playsRace(Race.Terran));
        assertFalse(build.playsRace(Race.Protoss));
        assertFalse(build.playsRace(Race.Zerg));
        assertFalse(build.isOpener());
        assertTrue(build.needLair());
    }

    @Test
    void wantsNoHydralisksUntilTheDenStands() {
        assertEquals(0, TwoHatchHydraTerran.desiredHydralisks(false, true, 0));
        assertEquals(0, TwoHatchHydraTerran.desiredHydralisks(false, false, 2000));
    }

    @Test
    void knownMechWantsMoreHydralisksThanTheSafeComposition() {
        assertEquals(TwoHatchHydraTerran.MECH_HYDRALISKS, TwoHatchHydraTerran.desiredHydralisks(true, true, 0));
        assertEquals(TwoHatchHydraTerran.BASE_HYDRALISKS, TwoHatchHydraTerran.desiredHydralisks(true, false, 0));
        assertTrue(TwoHatchHydraTerran.MECH_HYDRALISKS > TwoHatchHydraTerran.BASE_HYDRALISKS);
    }

    @Test
    void floatingMineralsRaiseTheTargetUpToTheCap() {
        int floating = LarvaBoundMacroHatchery.FLOAT_MINERALS;

        assertEquals(TwoHatchHydraTerran.BASE_HYDRALISKS + floating / 75,
                TwoHatchHydraTerran.desiredHydralisks(true, false, floating));
        assertEquals(TwoHatchHydraTerran.BASE_HYDRALISKS + TwoHatchHydraTerran.FLOAT_HYDRALISK_CAP,
                TwoHatchHydraTerran.desiredHydralisks(true, false, 100000));
        assertEquals(TwoHatchHydraTerran.BASE_HYDRALISKS,
                TwoHatchHydraTerran.desiredHydralisks(true, false, floating - 1));
    }

    @Test
    void theDenUpgradesComeEarlierAgainstKnownMech() {
        assertFalse(TwoHatchHydraTerran.shouldPlanDenUpgrades(1, true));
        assertTrue(TwoHatchHydraTerran.shouldPlanDenUpgrades(TwoHatchHydraTerran.MECH_DEN_UPGRADE_HYDRALISKS, true));
        assertFalse(TwoHatchHydraTerran.shouldPlanDenUpgrades(
                TwoHatchHydraTerran.MECH_DEN_UPGRADE_HYDRALISKS, false));
        assertTrue(TwoHatchHydraTerran.shouldPlanDenUpgrades(TwoHatchHydraTerran.DEN_UPGRADE_HYDRALISKS, false));
    }

    @Test
    void theEvolutionUpgradesComeEarlierAgainstKnownMech() {
        assertFalse(TwoHatchHydraTerran.shouldPlanEvolutionUpgrades(5, true));
        assertTrue(TwoHatchHydraTerran.shouldPlanEvolutionUpgrades(
                TwoHatchHydraTerran.MECH_EVOLUTION_UPGRADE_HYDRALISKS, true));
        assertFalse(TwoHatchHydraTerran.shouldPlanEvolutionUpgrades(
                TwoHatchHydraTerran.MECH_EVOLUTION_UPGRADE_HYDRALISKS, false));
        assertTrue(TwoHatchHydraTerran.shouldPlanEvolutionUpgrades(
                TwoHatchHydraTerran.EVOLUTION_UPGRADE_HYDRALISKS, false));
    }

    @Test
    void theDenUpgradesOutrankTheEvolutionUpgrades() {
        assertTrue(TwoHatchHydraTerran.MECH_DEN_UPGRADE_HYDRALISKS
                < TwoHatchHydraTerran.MECH_EVOLUTION_UPGRADE_HYDRALISKS);
        assertTrue(TwoHatchHydraTerran.DEN_UPGRADE_HYDRALISKS < TwoHatchHydraTerran.EVOLUTION_UPGRADE_HYDRALISKS);
    }

    @Test
    void speedAndRangeMoveAheadOfTheHydraliskStreamBeforeTheEvolutionUpgrades() {
        TwoHatchHydraTerran build = new TwoHatchHydraTerran();

        int denTrigger = TwoHatchHydraTerran.HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY;
        int evolutionTrigger = TwoHatchHydraTerran.HYDRALISKS_BEFORE_EVOLUTION_UPGRADE_PRIORITY;

        assertFalse(build.isArmyUpgradeTriggered(UpgradeType.Muscular_Augments, hydralisks(denTrigger - 1)));
        assertTrue(build.isArmyUpgradeTriggered(UpgradeType.Muscular_Augments, hydralisks(denTrigger)));
        assertFalse(build.isArmyUpgradeTriggered(UpgradeType.Grooved_Spines, hydralisks(denTrigger - 1)));
        assertTrue(build.isArmyUpgradeTriggered(UpgradeType.Grooved_Spines, hydralisks(denTrigger)));
        assertFalse(build.isArmyUpgradeTriggered(UpgradeType.Zerg_Missile_Attacks, hydralisks(evolutionTrigger - 1)));
        assertTrue(build.isArmyUpgradeTriggered(UpgradeType.Zerg_Missile_Attacks, hydralisks(evolutionTrigger)));
        assertFalse(build.isArmyUpgradeTriggered(UpgradeType.Zerg_Carapace, hydralisks(evolutionTrigger - 1)));
        assertTrue(build.isArmyUpgradeTriggered(UpgradeType.Zerg_Carapace, hydralisks(evolutionTrigger)));
    }

    private static UnitTypeCount hydralisks(int living) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < living; i++) {
            count.addUnit(UnitType.Zerg_Hydralisk);
        }
        return count;
    }

    @Test
    void theDroneRoundArmyIsHydraliskOnly() {
        TwoHatchHydraTerran build = new TwoHatchHydraTerran();

        assertEquals(Collections.singleton(UnitType.Zerg_Hydralisk), build.droneRoundArmy());
    }

    @Test
    void takesTheThirdBaseOnlyOnceTheHydralisksAreOut() {
        int out = TwoHatchHydraTerran.HYDRALISKS_BEFORE_THIRD_BASE;

        assertFalse(TwoHatchHydraTerran.wantsThirdBase(2, out - 1));
        assertFalse(TwoHatchHydraTerran.wantsThirdBase(1, out));
        assertFalse(TwoHatchHydraTerran.wantsThirdBase(3, out));
        assertTrue(TwoHatchHydraTerran.wantsThirdBase(2, out));
    }

    @Test
    void dronesOutrankTheHydraliskStreamOnlyAfterTheFirstWaveAndBelowTheTarget() {
        int wave = TwoHatchHydraTerran.HYDRALISKS_BEFORE_DRONES;
        int target = TwoHatchHydraTerran.DRONES_BEFORE_HYDRALISKS;

        assertTrue(TwoHatchHydraTerran.shouldDroneBeforeHydralisks(true, wave, target - 1));
        assertFalse(TwoHatchHydraTerran.shouldDroneBeforeHydralisks(true, wave, target));
        assertFalse(TwoHatchHydraTerran.shouldDroneBeforeHydralisks(true, wave - 1, 11));
        assertFalse(TwoHatchHydraTerran.shouldDroneBeforeHydralisks(false, wave, 11));
    }

    @Test
    void hydralisksComeFirstThenDronesToEighteenThenTheRestOfTheStream() {
        int drones = 11;
        assertEquals(UnitType.Zerg_Hydralisk,
                TwoHatchHydraTerran.nextArmyUnit(true, 0, 12, true, 0, 0, drones, 21));
        assertEquals(UnitType.Zerg_Hydralisk,
                TwoHatchHydraTerran.nextArmyUnit(true, 5, 12, true, 0, 0, drones, 21));
        assertEquals(UnitType.Zerg_Drone,
                TwoHatchHydraTerran.nextArmyUnit(true, 6, 12, true, 0, 0, drones, 21));
        assertEquals(UnitType.Zerg_Hydralisk,
                TwoHatchHydraTerran.nextArmyUnit(true, 6, 12, true, 0, 0, 18, 21));
        assertEquals(UnitType.Zerg_Zergling,
                TwoHatchHydraTerran.nextArmyUnit(true, 12, 12, true, 0, 8, 18, 21));
        assertEquals(UnitType.Zerg_Drone,
                TwoHatchHydraTerran.nextArmyUnit(true, 12, 12, true, 8, 8, 18, 21));
        assertEquals(null, TwoHatchHydraTerran.nextArmyUnit(true, 12, 12, true, 8, 8, 21, 21));
    }

    @Test
    void aBlockedHydraliskPlanFallsThroughToZerglingsAndDrones() {
        assertEquals(UnitType.Zerg_Zergling,
                TwoHatchHydraTerran.nextArmyUnit(true, 3, 12, false, 0, 4, 11, 21));
        assertEquals(UnitType.Zerg_Drone,
                TwoHatchHydraTerran.nextArmyUnit(true, 3, 12, false, 4, 4, 11, 21));
    }

    @Test
    void theArmyStepNeverMorphsALurkerOrPlansLurkerAspect() {
        for (boolean den : new boolean[] {false, true}) {
            for (boolean canHydra : new boolean[] {false, true}) {
                for (int hydras = 0; hydras <= 60; hydras += 3) {
                    for (int lings = 0; lings <= 20; lings += 5) {
                        for (int drones = 0; drones <= 30; drones += 3) {
                            UnitType next = TwoHatchHydraTerran.nextArmyUnit(den, hydras, 24, canHydra, lings, 10,
                                    drones, 21);
                            assertTrue(next == null || next == UnitType.Zerg_Hydralisk
                                    || next == UnitType.Zerg_Zergling || next == UnitType.Zerg_Drone, "" + next);
                        }
                    }
                }
            }
        }
        UpgradeType[] armyUpgrades = {UpgradeType.Muscular_Augments, UpgradeType.Grooved_Spines,
            UpgradeType.Zerg_Missile_Attacks, UpgradeType.Zerg_Carapace};
        for (UpgradeType upgrade : armyUpgrades) {
            assertTrue(new TwoHatchHydraTerran().armyUpgradeTrigger(upgrade) != null, upgrade.toString());
        }
        assertEquals(null, new TwoHatchHydraTerran().armyUpgradeTrigger(UpgradeType.Pneumatized_Carapace));
    }

    @Test
    void expandsWhenBehindFloatingOrForTheHandoverBase() {
        assertFalse(TwoHatchHydraTerran.wantsExpansion(false, false, 15, 2));
        assertTrue(TwoHatchHydraTerran.wantsExpansion(true, false, 0, 3));
        assertTrue(TwoHatchHydraTerran.wantsExpansion(false, true, 0, 3));
        assertTrue(TwoHatchHydraTerran.wantsExpansion(false, false, TwoHatchHydraTerran.MECH_HYDRALISKS, 2));
        assertFalse(TwoHatchHydraTerran.wantsExpansion(false, false, TwoHatchHydraTerran.MECH_HYDRALISKS, 3));
    }

    @Test
    void theDroneTargetStartsAtEighteenAndReachesTheHandoverEconomyOnThreeBases() {
        assertEquals(18, TwoHatchHydraTerran.droneTarget(0, 2));
        assertEquals(21, TwoHatchHydraTerran.droneTarget(1, 2));
        assertEquals(27, TwoHatchHydraTerran.droneTarget(1, 3));
        assertEquals(TwoHatchHydraTerran.DRONES_BEFORE_HYDRALISKS, TwoHatchHydraTerran.droneTarget(0, 2));
        assertTrue(TwoHatchHydraTerran.droneTarget(1, 3) >= LurkerDefilerUltraTransition.ECONOMY_DRONES);
    }

    @Test
    void theBuildIsDistinctFromEveryOtherTerranBuildByName() {
        assertEquals(4, new HashSet<>(Arrays.asList(new TwoHatchHydraTerran().getName(),
                new ThreeHatchLurker().getName(), new TwoHatchMuta().getName(), new CrazyZerg().getName())).size());
    }
}
