package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.UnitTypeCount;
import org.junit.jupiter.api.Test;
import strategy.buildorder.LarvaBoundMacroHatchery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
                TwoHatchHydraTerran.nextArmyUnit(true, 0, 12, true, false, drones, 21, true));
        assertEquals(UnitType.Zerg_Hydralisk,
                TwoHatchHydraTerran.nextArmyUnit(true, 5, 12, true, false, drones, 21, true));
        assertEquals(UnitType.Zerg_Drone,
                TwoHatchHydraTerran.nextArmyUnit(true, 6, 12, true, false, drones, 21, true));
        assertEquals(UnitType.Zerg_Hydralisk,
                TwoHatchHydraTerran.nextArmyUnit(true, 6, 12, true, false, 18, 21, true));
        assertEquals(UnitType.Zerg_Zergling,
                TwoHatchHydraTerran.nextArmyUnit(true, 12, 12, true, true, 18, 21, true));
        assertEquals(UnitType.Zerg_Drone,
                TwoHatchHydraTerran.nextArmyUnit(true, 12, 12, true, false, 18, 21, true));
        assertEquals(null, TwoHatchHydraTerran.nextArmyUnit(true, 12, 12, true, false, 21, 21, true));
    }

    @Test
    void aBlockedHydraliskPlanFallsThroughToZerglingsAndDrones() {
        assertEquals(UnitType.Zerg_Zergling,
                TwoHatchHydraTerran.nextArmyUnit(true, 3, 12, false, true, 11, 21, true));
        assertEquals(UnitType.Zerg_Drone,
                TwoHatchHydraTerran.nextArmyUnit(true, 3, 12, false, false, 11, 21, true));
    }

    @Test
    void aBlockedDronePlanNeverStarvesTheHydraliskOrZerglingStream() {
        assertEquals(UnitType.Zerg_Hydralisk,
                TwoHatchHydraTerran.nextArmyUnit(true, 6, 12, true, false, 11, 21, false));
        assertEquals(UnitType.Zerg_Zergling,
                TwoHatchHydraTerran.nextArmyUnit(true, 12, 12, true, true, 11, 21, false));
        assertEquals(null, TwoHatchHydraTerran.nextArmyUnit(true, 12, 12, true, false, 11, 21, false));
    }

    @Test
    void theBuildSourceNeverNamesALurkerMorphOrLurkerAspect() throws IOException {
        String[] files = {"strategy/buildorder/terran/TwoHatchHydraTerran.java",
            "strategy/buildorder/terran/TerranBase.java"};
        for (String file : files) {
            String source = new String(Files.readAllBytes(Paths.get("src/main/java", file)), StandardCharsets.UTF_8);
            for (String banned : new String[] {"Zerg_Lurker", "Lurker_Aspect", "planTech(", "planLurker"}) {
                assertFalse(source.contains(banned), file + " names " + banned);
            }
        }
    }

    @Test
    void onlyTheFourArmyUpgradesMoveAheadOfTheHydraliskStream() {
        TwoHatchHydraTerran build = new TwoHatchHydraTerran();
        UpgradeType[] armyUpgrades = {UpgradeType.Muscular_Augments, UpgradeType.Grooved_Spines,
            UpgradeType.Zerg_Missile_Attacks, UpgradeType.Zerg_Carapace};
        for (UpgradeType upgrade : armyUpgrades) {
            assertTrue(build.armyUpgradeTrigger(upgrade) != null, upgrade.toString());
        }
        assertEquals(null, build.armyUpgradeTrigger(UpgradeType.Pneumatized_Carapace));
    }

    @Test
    void expandsWhenBehindFloatingOrForTheHandoverBase() {
        int wave = TwoHatchHydraTerran.HYDRALISKS_BEFORE_THIRD_BASE;
        assertFalse(TwoHatchHydraTerran.wantsExpansion(false, false, 15, 2));
        assertTrue(TwoHatchHydraTerran.wantsExpansion(true, false, wave, 3));
        assertTrue(TwoHatchHydraTerran.wantsExpansion(false, true, wave, 3));
        assertTrue(TwoHatchHydraTerran.wantsExpansion(false, false, TwoHatchHydraTerran.MECH_HYDRALISKS, 2));
        assertFalse(TwoHatchHydraTerran.wantsExpansion(false, false, TwoHatchHydraTerran.MECH_HYDRALISKS, 3));
    }

    @Test
    void noHatcheryBeyondTheNaturalIsAskedForBeforeTheFirstHydraliskWave() {
        int wave = TwoHatchHydraTerran.HYDRALISKS_BEFORE_THIRD_BASE;

        assertFalse(TwoHatchHydraTerran.firstHydraliskWaveProduced(0));
        assertFalse(TwoHatchHydraTerran.firstHydraliskWaveProduced(wave - 1));
        assertTrue(TwoHatchHydraTerran.firstHydraliskWaveProduced(wave));

        assertFalse(TwoHatchHydraTerran.wantsExpansion(true, true, 0, 2));
        assertFalse(TwoHatchHydraTerran.wantsExpansion(true, true, wave - 1, 2));
        assertTrue(TwoHatchHydraTerran.wantsExpansion(true, false, wave, 2));
    }

    @Test
    void theMacroHatcheryWaitsForTheDenAndTheFirstHydraliskWave() {
        int wave = TwoHatchHydraTerran.HYDRALISKS_BEFORE_THIRD_BASE;

        assertFalse(TwoHatchHydraTerran.macroHatcheryAllowed(true, 0));
        assertFalse(TwoHatchHydraTerran.macroHatcheryAllowed(true, wave - 1));
        assertFalse(TwoHatchHydraTerran.macroHatcheryAllowed(false, wave));
        assertTrue(TwoHatchHydraTerran.macroHatcheryAllowed(true, wave));
    }

    @Test
    void theMacroHatcheryGateIsWiredThroughTheGameStateHook() throws NoSuchMethodException {
        assertEquals(TwoHatchHydraTerran.class, TwoHatchHydraTerran.class
                .getDeclaredMethod("macroHatcheryReady", info.GameState.class).getDeclaringClass());
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
