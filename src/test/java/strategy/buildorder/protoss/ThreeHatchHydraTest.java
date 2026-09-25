package strategy.buildorder.protoss;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.AdvancedUnitEligibility;
import macro.ProductionQueue;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import telemetry.PlanEvents;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchHydraTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final int HYDRALISK_TARGET = 18;

    private static final int DEN_COMPLETE_FRAME = 5964;

    private static final int FRAMES_WITHOUT_LARVA = 3087;

    private static final int HYDRALISKS_FOR_UPGRADES = 7;

    private static final int UPGRADE_PASSES = 5;

    private static TechProgression withDen() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);
        techProgression.setHydraliskDen(true);
        return techProgression;
    }

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    @Test
    void withholdsSpeedWhileTheZerglingsAreStillPlanned() {
        assertFalse(ThreeHatchHydra.shouldPlanMetabolicBoost(true, 0));
    }

    @Test
    void withholdsSpeedOnTheZerglingCountAlone() {
        assertFalse(ThreeHatchHydra.shouldPlanMetabolicBoost(true, 12));
    }

    @Test
    void takesSpeedOnceTheZerglingsAreFielded() {
        assertTrue(ThreeHatchHydra.shouldPlanMetabolicBoost(true, 13));
    }

    @Test
    void withholdsSpeedWhileTheUpgradeIsUnavailable() {
        assertFalse(ThreeHatchHydra.shouldPlanMetabolicBoost(false, 20));
    }

    @Test
    void queuesTheHydraliskAtTheAdvancedUnitPriorityOnceTheDenIsComplete() {
        UnitTypeCount count = new UnitTypeCount();

        List<Plan> plans = ThreeHatchHydra.planHydralisk(withDen(), HYDRALISK_TARGET, GATHERER_FLOOR, 0, count);

        assertEquals(1, plans.size());
        assertEquals(UnitType.Zerg_Hydralisk, plans.get(0).getPlannedUnit());
        assertEquals(UnitPlan.ADVANCED_UNIT_PRIORITY, plans.get(0).getPriority());
        assertEquals(1, count.plannedCount(UnitType.Zerg_Hydralisk));
    }

    @Test
    void withholdsTheHydraliskWithoutADen() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);

        assertTrue(ThreeHatchHydra.planHydralisk(techProgression, HYDRALISK_TARGET, GATHERER_FLOOR, 0, new UnitTypeCount()).isEmpty());
    }

    @Test
    void withholdsTheHydraliskOnceTheTargetIsMet() {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < HYDRALISK_TARGET; i++) {
            count.addUnit(UnitType.Zerg_Hydralisk);
        }

        assertTrue(ThreeHatchHydra.planHydralisk(withDen(), HYDRALISK_TARGET, GATHERER_FLOOR, 0, count).isEmpty());
    }

    /**
     * Game LSWLD0GP queued 18 Hydralisk plans in one burst behind the Drones. With no larva to
     * take the head plan, every frame of the wait still leaves a single Hydralisk queued.
     */
    @Test
    void queuesOneHydraliskAtATimeWhileNoLarvaTakesIt() {
        ProductionQueue queue = new ProductionQueue();
        UnitTypeCount count = new UnitTypeCount();

        for (int frame = 0; frame < FRAMES_WITHOUT_LARVA; frame++) {
            queue.addAll(ThreeHatchHydra.planHydralisk(withDen(), HYDRALISK_TARGET, GATHERER_FLOOR,
                    queue.unitPlanCount(UnitType.Zerg_Hydralisk), count));
        }

        assertEquals(1, queue.unitPlanCount(UnitType.Zerg_Hydralisk));
        assertEquals(1, count.plannedCount(UnitType.Zerg_Hydralisk));
    }

    @Test
    void aDroneQueuedAfterTheDenDoesNotSortAheadOfTheQueuedHydralisk() {
        ProductionQueue queue = new ProductionQueue();
        queue.addAll(ThreeHatchHydra.planHydralisk(withDen(), HYDRALISK_TARGET, GATHERER_FLOOR, 0, new UnitTypeCount()));
        Plan hydralisk = queue.toSortedList().get(0);

        queue.add(new UnitPlan(UnitType.Zerg_Drone, DEN_COMPLETE_FRAME));

        assertSame(hydralisk, queue.poll());
    }

    @Test
    void reachesAChamberPerUpgradeLineOneAtATime() {
        TechProgression techProgression = withDen();
        int mostQueuedInOnePass = 0;
        for (int pass = 0; pass < UPGRADE_PASSES; pass++) {
            int queued = 0;
            if (ThreeHatchHydra.wantEvolutionChamber(techProgression, HYDRALISKS_FOR_UPGRADES)) {
                techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() + 1);
                queued++;
            }
            if (ThreeHatchHydra.wantEvolutionChamber(techProgression, HYDRALISKS_FOR_UPGRADES)) {
                techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() + 1);
                queued++;
            }
            mostQueuedInOnePass = Math.max(mostQueuedInOnePass, queued);
            if (techProgression.getPlannedEvolutionChambers() > 0) {
                techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() - 1);
                techProgression.setEvolutionChambers(techProgression.getEvolutionChambers() + 1);
            }
        }

        assertEquals(ThreeHatchHydra.UPGRADE_EVOLUTION_CHAMBERS, techProgression.getEvolutionChambers());
        assertEquals(1, mostQueuedInOnePass);
    }

    @Test
    void aChamberTheSporeQueuedThisPassHoldsTheUpgradeChamber() {
        TechProgression techProgression = withDen();
        techProgression.setPlannedEvolutionChambers(1);

        assertFalse(ThreeHatchHydra.wantEvolutionChamber(techProgression, HYDRALISKS_FOR_UPGRADES));
    }

    @Test
    void aSporeRequirementAloneDoesNotAskForAnUpgradeChamber() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);

        assertFalse(ThreeHatchHydra.wantEvolutionChamber(techProgression, HYDRALISKS_FOR_UPGRADES));
    }

    private static final int UPGRADE_QUEUED_FRAME = 6236;

    private static UnitTypeCount hydralisks(int planned, int living) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < planned; i++) {
            count.planUnit(UnitType.Zerg_Hydralisk);
        }
        for (int i = 0; i < living; i++) {
            count.addUnit(UnitType.Zerg_Hydralisk);
        }
        return count;
    }

    private static int upgradePriority(UpgradeType upgradeType, int livingHydralisks) {
        return new ThreeHatchHydra().upgradePriority(upgradeType, hydralisks(0, livingHydralisks), UPGRADE_QUEUED_FRAME);
    }

    @Test
    void hydraliskDenUpgradesKeepTheirFramePriorityBelowTheTrigger() {
        int below = ThreeHatchHydra.HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY - 1;

        assertEquals(UPGRADE_QUEUED_FRAME, upgradePriority(UpgradeType.Muscular_Augments, below));
        assertEquals(UPGRADE_QUEUED_FRAME, upgradePriority(UpgradeType.Grooved_Spines, below));
    }

    @Test
    void hydraliskDenUpgradesPollAheadOfHydralisksAtTheTrigger() {
        int at = ThreeHatchHydra.HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY;

        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Muscular_Augments, at));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Grooved_Spines, at));
        assertTrue(BuildOrder.ARMY_UPGRADE_PRIORITY < UnitPlan.ADVANCED_UNIT_PRIORITY);
    }

    @Test
    void hydraliskDenUpgradesPollAheadOfHydralisksAboveTheTrigger() {
        int above = ThreeHatchHydra.HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY + 1;

        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Muscular_Augments, above));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Grooved_Spines, above));
    }

    @Test
    void evolutionUpgradesMoveAtTheirOwnTrigger() {
        int trigger = ThreeHatchHydra.HYDRALISKS_BEFORE_EVOLUTION_UPGRADE_PRIORITY;

        assertEquals(UPGRADE_QUEUED_FRAME, upgradePriority(UpgradeType.Zerg_Missile_Attacks, trigger - 1));
        assertEquals(UPGRADE_QUEUED_FRAME, upgradePriority(UpgradeType.Zerg_Carapace, trigger - 1));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Zerg_Missile_Attacks, trigger));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, upgradePriority(UpgradeType.Zerg_Carapace, trigger + 1));
    }

    @Test
    void plannedHydralisksDoNotCountTowardTheTrigger() {
        UnitTypeCount count = hydralisks(ThreeHatchHydra.HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY, 0);

        assertEquals(UPGRADE_QUEUED_FRAME,
                new ThreeHatchHydra().upgradePriority(UpgradeType.Muscular_Augments, count, UPGRADE_QUEUED_FRAME));
    }

    @Test
    void upgradesWithoutATriggerKeepTheirFramePriority() {
        assertEquals(UPGRADE_QUEUED_FRAME, upgradePriority(UpgradeType.Metabolic_Boost, 20));
        assertEquals(UPGRADE_QUEUED_FRAME, upgradePriority(UpgradeType.Pneumatized_Carapace, 20));
    }
}
