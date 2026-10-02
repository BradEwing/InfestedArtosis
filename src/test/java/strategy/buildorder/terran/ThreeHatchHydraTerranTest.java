package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import org.junit.jupiter.api.Test;
import strategy.buildorder.ArmyUpgradeTrigger;
import strategy.buildorder.LarvaBoundMacroHatchery;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchHydraTerranTest {

    @Test
    void isATerranBuildThatPlaysOnlyTerran() {
        ThreeHatchHydraTerran build = new ThreeHatchHydraTerran();

        assertTrue(build instanceof TerranBase);
        assertEquals("3HatchHydraZvT", build.getName());
        assertTrue(build.playsRace(Race.Terran));
        assertFalse(build.playsRace(Race.Protoss));
        assertFalse(build.playsRace(Race.Zerg));
        assertFalse(build.isOpener());
        assertTrue(build.needLair());
    }

    @Test
    void wantsNoHydralisksUntilTheDenStands() {
        assertEquals(0, ThreeHatchHydraTerran.desiredHydralisks(false, true, 0));
        assertEquals(0, ThreeHatchHydraTerran.desiredHydralisks(false, false, 2000));
    }

    @Test
    void knownMechWantsMoreHydralisksThanTheSafeComposition() {
        assertEquals(ThreeHatchHydraTerran.MECH_HYDRALISKS, ThreeHatchHydraTerran.desiredHydralisks(true, true, 0));
        assertEquals(ThreeHatchHydraTerran.BASE_HYDRALISKS, ThreeHatchHydraTerran.desiredHydralisks(true, false, 0));
        assertTrue(ThreeHatchHydraTerran.MECH_HYDRALISKS > ThreeHatchHydraTerran.BASE_HYDRALISKS);
    }

    @Test
    void floatingMineralsRaiseTheTargetUpToTheCap() {
        int floating = LarvaBoundMacroHatchery.FLOAT_MINERALS;

        assertEquals(ThreeHatchHydraTerran.BASE_HYDRALISKS + floating / 75,
                ThreeHatchHydraTerran.desiredHydralisks(true, false, floating));
        assertEquals(ThreeHatchHydraTerran.BASE_HYDRALISKS + ThreeHatchHydraTerran.FLOAT_HYDRALISK_CAP,
                ThreeHatchHydraTerran.desiredHydralisks(true, false, 100000));
        assertEquals(ThreeHatchHydraTerran.BASE_HYDRALISKS,
                ThreeHatchHydraTerran.desiredHydralisks(true, false, floating - 1));
    }

    @Test
    void theDenUpgradesComeEarlierAgainstKnownMech() {
        assertFalse(ThreeHatchHydraTerran.shouldPlanDenUpgrades(1, true));
        assertTrue(ThreeHatchHydraTerran.shouldPlanDenUpgrades(ThreeHatchHydraTerran.MECH_DEN_UPGRADE_HYDRALISKS, true));
        assertFalse(ThreeHatchHydraTerran.shouldPlanDenUpgrades(
                ThreeHatchHydraTerran.MECH_DEN_UPGRADE_HYDRALISKS, false));
        assertTrue(ThreeHatchHydraTerran.shouldPlanDenUpgrades(ThreeHatchHydraTerran.DEN_UPGRADE_HYDRALISKS, false));
    }

    @Test
    void theEvolutionUpgradesComeEarlierAgainstKnownMech() {
        assertFalse(ThreeHatchHydraTerran.shouldPlanEvolutionUpgrades(5, true));
        assertTrue(ThreeHatchHydraTerran.shouldPlanEvolutionUpgrades(
                ThreeHatchHydraTerran.MECH_EVOLUTION_UPGRADE_HYDRALISKS, true));
        assertFalse(ThreeHatchHydraTerran.shouldPlanEvolutionUpgrades(
                ThreeHatchHydraTerran.MECH_EVOLUTION_UPGRADE_HYDRALISKS, false));
        assertTrue(ThreeHatchHydraTerran.shouldPlanEvolutionUpgrades(
                ThreeHatchHydraTerran.EVOLUTION_UPGRADE_HYDRALISKS, false));
    }

    @Test
    void theDenUpgradesOutrankTheEvolutionUpgrades() {
        assertTrue(ThreeHatchHydraTerran.MECH_DEN_UPGRADE_HYDRALISKS
                < ThreeHatchHydraTerran.MECH_EVOLUTION_UPGRADE_HYDRALISKS);
        assertTrue(ThreeHatchHydraTerran.DEN_UPGRADE_HYDRALISKS < ThreeHatchHydraTerran.EVOLUTION_UPGRADE_HYDRALISKS);
    }

    @Test
    void speedAndRangeMoveAheadOfTheHydraliskStreamBeforeTheEvolutionUpgrades() {
        ThreeHatchHydraTerran build = new ThreeHatchHydraTerran();

        ArmyUpgradeTrigger speed = build.armyUpgradeTrigger(UpgradeType.Muscular_Augments);
        ArmyUpgradeTrigger range = build.armyUpgradeTrigger(UpgradeType.Grooved_Spines);
        ArmyUpgradeTrigger missile = build.armyUpgradeTrigger(UpgradeType.Zerg_Missile_Attacks);
        ArmyUpgradeTrigger carapace = build.armyUpgradeTrigger(UpgradeType.Zerg_Carapace);

        assertNotNull(speed);
        assertNotNull(range);
        assertNotNull(missile);
        assertNotNull(carapace);
        assertNull(build.armyUpgradeTrigger(UpgradeType.Metabolic_Boost));
    }

    @Test
    void theArmyIsHydraliskOnlySoNoLurkerMorphTakesTheHydraCore() {
        ThreeHatchHydraTerran build = new ThreeHatchHydraTerran();

        assertEquals(Collections.singleton(UnitType.Zerg_Hydralisk), build.droneRoundArmy());
    }

    @Test
    void takesTheThirdBaseOnceTheLairAndTwentyDronesStand() {
        assertFalse(ThreeHatchHydraTerran.wantsThirdBase(2, 19, 1));
        assertFalse(ThreeHatchHydraTerran.wantsThirdBase(2, 20, 0));
        assertFalse(ThreeHatchHydraTerran.wantsThirdBase(1, 30, 1));
        assertFalse(ThreeHatchHydraTerran.wantsThirdBase(3, 30, 1));
        assertTrue(ThreeHatchHydraTerran.wantsThirdBase(2, 20, 1));
    }

    @Test
    void expandsWhenBehindFloatingOrForTheHandoverBase() {
        assertFalse(ThreeHatchHydraTerran.wantsExpansion(false, false, 15, 2));
        assertTrue(ThreeHatchHydraTerran.wantsExpansion(true, false, 0, 3));
        assertTrue(ThreeHatchHydraTerran.wantsExpansion(false, true, 0, 3));
        assertTrue(ThreeHatchHydraTerran.wantsExpansion(false, false, 16, 2));
        assertFalse(ThreeHatchHydraTerran.wantsExpansion(false, false, 16, 3));
    }

    @Test
    void theDroneTargetReachesTheHandoverEconomyOnThreeBases() {
        assertEquals(12, ThreeHatchHydraTerran.droneTarget(0, 2));
        assertEquals(21, ThreeHatchHydraTerran.droneTarget(1, 2));
        assertEquals(27, ThreeHatchHydraTerran.droneTarget(1, 3));
        assertTrue(ThreeHatchHydraTerran.droneTarget(1, 3) >= LurkerDefilerUltraTransition.ECONOMY_DRONES);
    }

    @Test
    void theBuildIsDistinctFromEveryOtherTerranBuildByName() {
        assertEquals(4, new HashSet<>(Arrays.asList(new ThreeHatchHydraTerran().getName(),
                new ThreeHatchLurker().getName(), new TwoHatchMuta().getName(), new CrazyZerg().getName())).size());
    }
}
