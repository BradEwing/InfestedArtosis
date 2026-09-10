package strategy.buildorder;

import bwapi.UnitType;
import info.ResourceCount;
import info.TechProgression;
import info.UnitTypeCount;
import macro.AdvancedUnitEligibility;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanComparator;
import macro.plan.PlanState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import strategy.buildorder.opener.FourPool;
import strategy.buildorder.opener.NinePoolSpeed;
import strategy.buildorder.opener.Overpool;
import strategy.buildorder.opener.ThreeHatchBeforePool;
import strategy.buildorder.opener.TwelvePool;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildOrderTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final int EMERGENCY_ZERGLING_TARGET = 6;

    private static final int FRAMES = 30;

    private static final int EMERGENCY_PRIORITY = 0;

    private static final int LAIR_PRIORITY = 3;

    private static final int SPIRE_PRIORITY = 4;

    private static final int HATCHERY_FRAME = 203;

    private static final int POOL_FRAME = 204;

    private static final int EXTRACTOR_FRAME = 205;

    private static final int SURPLUS_BANK = 900;

    private static final int IDLE_BANK = 2000;

    private static final int IDLE_LARVA = 4;

    private static final int IDLE_QUEUE_DEPTH = 1;

    private final List<String> withheld = new ArrayList<>();

    private PlanEventSink recorder() {
        return new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
            }

            @Override
            public void onWithheld(UnitType unitType, PlanBlocker blocker) {
                withheld.add(unitType + ":" + blocker);
            }
        };
    }

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    @Test
    void theGateWithholdsAMutaliskBelowTheGathererFloorAndReportsIt() {
        PlanEvents.register(recorder());

        assertFalse(BuildOrder.canPlanAdvancedUnit(UnitType.Zerg_Mutalisk, withSpire(), GATHERER_FLOOR - 1));
        assertEquals(1, withheld.size());
        assertEquals("Zerg_Mutalisk:INSUFFICIENT_GATHERERS", withheld.get(0));
    }

    @Test
    void theGateReportsMissingTechBeforeGatherers() {
        PlanEvents.register(recorder());

        assertFalse(BuildOrder.canPlanAdvancedUnit(UnitType.Zerg_Scourge, new TechProgression(), 0));
        assertEquals("Zerg_Scourge:TECH_MISSING", withheld.get(0));
    }

    @Test
    void theGatePassesAnEligibleUnitWithoutAnEvent() {
        PlanEvents.register(recorder());

        assertTrue(BuildOrder.canPlanAdvancedUnit(UnitType.Zerg_Mutalisk, withSpire(), GATHERER_FLOOR));
        assertTrue(withheld.isEmpty());
    }

    private int emergencyZerglingPlansOverFrames(boolean readLivingOnly) {
        UnitTypeCount count = new UnitTypeCount();
        int plans = 0;
        for (int frame = 0; frame < FRAMES; frame++) {
            int zerglings = readLivingOnly
                    ? count.livingCount(UnitType.Zerg_Zergling)
                    : count.get(UnitType.Zerg_Zergling);
            if (BuildOrder.shouldPlanEmergencyZergling(zerglings, EMERGENCY_ZERGLING_TARGET)) {
                count.planUnit(UnitType.Zerg_Zergling);
                plans++;
            }
        }
        return plans;
    }

    @Test
    void theEmergencyQueuesOnePlanPerPairAndThenStops() {
        assertEquals(EMERGENCY_ZERGLING_TARGET / 2, emergencyZerglingPlansOverFrames(false));
    }

    @Test
    void theEmergencyWouldFloodTheQueueIfItCountedOnlyHatchedZerglings() {
        assertEquals(FRAMES, emergencyZerglingPlansOverFrames(true));
    }

    @Test
    void theEmergencyStopsAtTheTargetItIsGiven() {
        assertFalse(BuildOrder.shouldPlanEmergencyZergling(EMERGENCY_ZERGLING_TARGET, EMERGENCY_ZERGLING_TARGET));
        assertTrue(BuildOrder.shouldPlanEmergencyZergling(EMERGENCY_ZERGLING_TARGET - 1, EMERGENCY_ZERGLING_TARGET));
    }

    private static Plan poolPlanFor(BuildOrder buildOrder) {
        return new BuildingPlan(UnitType.Zerg_Spawning_Pool, buildOrder.poolPriority(POOL_FRAME));
    }

    private static List<BuildOrder> poolFirstOpeners() {
        return Arrays.asList(new FourPool(), new NinePoolSpeed(), new TwelvePool(), new Overpool());
    }

    @Test
    void aPoolFirstOpenerOutranksTheBuildingsItCompetesWith() {
        PlanComparator comparator = new PlanComparator();

        for (BuildOrder opener : poolFirstOpeners()) {
            Plan pool = poolPlanFor(opener);
            assertEquals(BuildOrder.SPAWNING_POOL_PRIORITY, pool.getPriority(), opener.getName());
            assertTrue(comparator.compare(pool, new BuildingPlan(UnitType.Zerg_Extractor, EXTRACTOR_FRAME)) < 0, opener.getName());
            assertTrue(comparator.compare(pool, new BuildingPlan(UnitType.Zerg_Lair, LAIR_PRIORITY)) < 0, opener.getName());
            assertTrue(comparator.compare(pool, new BuildingPlan(UnitType.Zerg_Spire, SPIRE_PRIORITY)) < 0, opener.getName());
        }
    }

    @Test
    void aPoolFirstOpenerYieldsToTheReservedEmergencyPriority() {
        Plan emergency = new BuildingPlan(UnitType.Zerg_Sunken_Colony, EMERGENCY_PRIORITY);

        for (BuildOrder opener : poolFirstOpeners()) {
            assertTrue(new PlanComparator().compare(emergency, poolPlanFor(opener)) < 0, opener.getName());
        }
    }

    @Test
    void aHatchFirstOpenerKeepsThePoolBehindTheHatcheriesQueuedBeforeIt() {
        BuildOrder opener = new ThreeHatchBeforePool();
        Plan hatchery = new BuildingPlan(UnitType.Zerg_Hatchery, HATCHERY_FRAME);

        assertEquals(POOL_FRAME, opener.poolPriority(POOL_FRAME));
        assertTrue(new PlanComparator().compare(hatchery, poolPlanFor(opener)) < 0);
    }

    @Test
    void anExtractorQueuedAfterThePoolSortsBehindIt() {
        PlanComparator comparator = new PlanComparator();
        Plan extractor = new BuildingPlan(UnitType.Zerg_Extractor, EXTRACTOR_FRAME);

        assertTrue(comparator.compare(poolPlanFor(new ThreeHatchBeforePool()), extractor) < 0);
        for (BuildOrder opener : poolFirstOpeners()) {
            assertTrue(comparator.compare(poolPlanFor(opener), extractor) < 0, opener.getName());
        }
    }

    @Test
    void anExtractorQueuedAfterAHatcherySortsBehindIt() {
        Plan hatchery = new BuildingPlan(UnitType.Zerg_Hatchery, HATCHERY_FRAME);
        Plan extractor = new BuildingPlan(UnitType.Zerg_Extractor, EXTRACTOR_FRAME);

        assertTrue(new PlanComparator().compare(hatchery, extractor) < 0);
    }

    @Test
    void theHashDoesNotDependOnTheClassObjectIdentity() {
        BuildOrder order = new SpeedlingAllIn();
        assertEquals(java.util.Objects.hash(SpeedlingAllIn.class.getName(), order.getName()), order.hashCode());
    }

    @Test
    void twoInstancesOfOneBuildOrderAgreeOnHashAndEquality() {
        BuildOrder first = new SpeedlingAllIn();
        BuildOrder second = new SpeedlingAllIn();
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    /**
     * The surplus sink has to be buyable with the resource that is in surplus. Read off the unit
     * type rather than asserted from memory, since gas would make it useless to a build that is
     * floating minerals precisely because its gas is spoken for.
     */
    @Test
    void theSurplusUnitCostsMineralsAndNoGas() {
        assertEquals(0, BuildOrder.MINERAL_SURPLUS_UNIT.gasPrice());
        assertTrue(BuildOrder.MINERAL_SURPLUS_UNIT.mineralPrice() > 0);
    }

    @Test
    void mineralsAndLarvaTogetherBuyASurplusUnit() {
        assertTrue(BuildOrder.shouldSpendMineralSurplus(BuildOrder.MINERAL_SURPLUS, true, true, 0));
        assertTrue(BuildOrder.shouldSpendMineralSurplus(SURPLUS_BANK, true, true, 0));
    }

    @Test
    void aBankUnderTheBarIsNotASurplus() {
        assertFalse(BuildOrder.shouldSpendMineralSurplus(BuildOrder.MINERAL_SURPLUS - 1, true, true, 0));
    }

    /**
     * With no larva free to take, a surplus unit would sit behind the plans that own them.
     */
    @Test
    void surplusUnitsWaitForAFreeLarva() {
        assertFalse(BuildOrder.shouldSpendMineralSurplus(SURPLUS_BANK, false, true, 0));
    }

    @Test
    void surplusUnitsWaitForTheSpawningPool() {
        assertFalse(BuildOrder.shouldSpendMineralSurplus(SURPLUS_BANK, true, false, 0));
    }

    /**
     * IA-331 acceptance criterion 1 asks for units, not for a plan every frame the bank stays
     * high. The bound is on plans still waiting, so the surplus drains at the rate the queue
     * clears.
     */
    @Test
    void theSurplusQueueIsBounded() {
        assertTrue(BuildOrder.shouldSpendMineralSurplus(
                SURPLUS_BANK, true, true, BuildOrder.MAX_QUEUED_SURPLUS_PLANS - 1));
        assertFalse(BuildOrder.shouldSpendMineralSurplus(
                SURPLUS_BANK, true, true, BuildOrder.MAX_QUEUED_SURPLUS_PLANS));
    }

    /**
     * IA-331 acceptance criterion 5: available_minerals above 2,000 while larva exceed 3 and the
     * queue is nearly empty is the rich-and-idle signature, and it has to resolve into a plan.
     */
    @Test
    void theRichAndIdleSignatureBuysAUnit() {
        ResourceCount resourceCount = new ResourceCount(null);
        boolean larvaAvailable = resourceCount.canScheduleLarva(IDLE_LARVA, 0);

        assertTrue(BuildOrder.shouldSpendMineralSurplus(IDLE_BANK, larvaAvailable, true, IDLE_QUEUE_DEPTH));
    }

    /**
     * The larva gate reads {@link ResourceCount#canScheduleLarva}, the same authority the
     * scheduler uses, which adds back the larva a plan is already holding: assigning one removes
     * it from the larva set while its reservation still stands. Subtracting the reservation from
     * the set alone counts that larva twice, so a second larva that is genuinely free reads as
     * none and the surplus declines to drain.
     */
    @Test
    void aLarvaHeldByAPlanIsNotCountedAgainstTheSurplusTwice() {
        ResourceCount resourceCount = new ResourceCount(null);
        resourceCount.reserveUnit(UnitType.Zerg_Zergling);
        int freeLarva = 1;
        int larvaHeldByPlans = 1;

        assertTrue(resourceCount.canScheduleLarva(freeLarva, larvaHeldByPlans));
        assertTrue(BuildOrder.shouldSpendMineralSurplus(
                SURPLUS_BANK, resourceCount.canScheduleLarva(freeLarva, larvaHeldByPlans), true, 0));
        assertFalse(freeLarva - resourceCount.getReservedLarva() > 0);
    }

}
