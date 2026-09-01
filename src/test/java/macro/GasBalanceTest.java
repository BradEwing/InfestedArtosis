package macro;

import bwapi.TechType;
import bwapi.UnitType;
import bwapi.UpgradeType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.TechPlan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasBalanceTest {

    private static final int MARGIN = GasBalance.IDLE_GAS_MARGIN;

    private static final int INTERVAL = GasBalance.RELEASE_INTERVAL_FRAMES;

    private static final List<Plan> NOTHING = Collections.emptyList();

    private static Plan spire() {
        return new BuildingPlan(UnitType.Zerg_Spire, 1000);
    }

    private static Plan hatchery() {
        return new BuildingPlan(UnitType.Zerg_Hatchery, 1000);
    }

    private static Plan mutalisk() {
        return new UnitPlan(UnitType.Zerg_Mutalisk, 1000);
    }

    private static int demand(Iterable<Plan> queued, Iterable<Plan> scheduled) {
        return GasBalance.gasDemand(queued) + GasBalance.gasDemand(scheduled);
    }

    @Test
    void gasDemandSumsThePriceOfEveryWaitingPlan() {
        List<Plan> plans = Arrays.asList(spire(), mutalisk(), hatchery());

        int expected = UnitType.Zerg_Spire.gasPrice() + UnitType.Zerg_Mutalisk.gasPrice() + UnitType.Zerg_Hatchery.gasPrice();

        assertEquals(expected, GasBalance.gasDemand(plans));
    }

    @Test
    void upgradesAndResearchCountAsGasDemand() {
        Plan upgrade = new UpgradePlan(UpgradeType.Metabolic_Boost, 1000);
        Plan research = new TechPlan(TechType.Lurker_Aspect, 1000, false);

        int expected = UpgradeType.Metabolic_Boost.gasPrice(1) + TechType.Lurker_Aspect.gasPrice();

        assertEquals(expected, GasBalance.gasDemand(Arrays.asList(upgrade, research)));
    }

    @Test
    void gasWorkersStayWhileQueuedPlansNeedMoreGasThanIsBanked() {
        List<Plan> queued = Arrays.asList(spire(), mutalisk(), mutalisk(), mutalisk());
        int gasDemand = demand(queued, NOTHING);
        int gas = 440;
        int minerals = 50;

        assertTrue(gasDemand > gas);
        assertFalse(GasBalance.isGasIdle(gas, minerals, gasDemand));
        assertTrue(GasBalance.wantsGasWorkers(gas, minerals, gasDemand));
    }

    @Test
    void gasAQueuedPlanIsWaitingForIsNotSurplus() {
        int gas = 300;
        int minerals = 50;

        assertTrue(GasBalance.isGasIdle(gas, minerals, demand(NOTHING, NOTHING)));
        assertFalse(GasBalance.isGasIdle(gas, minerals, demand(Collections.singletonList(spire()), NOTHING)));
    }

    @Test
    void schedulingAMineralOnlyBuildingNeverReleasesAGasWorker() {
        Plan hatchery = hatchery();
        int gas = 300;
        int minerals = 200;

        int queuedDemand = demand(Collections.singletonList(hatchery), NOTHING);
        int scheduledDemand = demand(NOTHING, Collections.singletonList(hatchery));

        assertEquals(queuedDemand, scheduledDemand);
        assertFalse(GasBalance.isGasIdle(gas, minerals, queuedDemand));
        assertFalse(GasBalance.isGasIdle(gas, minerals, scheduledDemand));
    }

    @Test
    void schedulingAGasPlanKeepsItsDemand() {
        Plan spire = spire();
        Set<Plan> scheduled = new HashSet<>(Collections.singletonList(spire));

        assertEquals(demand(Collections.singletonList(spire), NOTHING), demand(NOTHING, scheduled));
    }

    @Test
    void gasAtTheMarginIsNotIdle() {
        int minerals = 100;

        assertFalse(GasBalance.isGasIdle(minerals + MARGIN, minerals, 0));
        assertTrue(GasBalance.isGasIdle(minerals + MARGIN + 1, minerals, 0));
    }

    @Test
    void demandRefillsTheGeyserWhateverTheMinerals() {
        int gasDemand = UnitType.Zerg_Spire.gasPrice();
        int gas = gasDemand - 1;

        assertTrue(GasBalance.wantsGasWorkers(gas, 0, gasDemand));
        assertTrue(GasBalance.wantsGasWorkers(gas, gas, gasDemand));
        assertTrue(GasBalance.wantsGasWorkers(gas, gas + 2000, gasDemand));
    }

    @Test
    void floatingMineralsRefillTheGeyserWithoutDemand() {
        int gas = 0;

        assertTrue(GasBalance.wantsGasWorkers(gas, GasBalance.FLOATING_MINERALS_MARGIN + 1, 0));
        assertFalse(GasBalance.wantsGasWorkers(gas, GasBalance.FLOATING_MINERALS_MARGIN, 0));
    }

    @Test
    void theReleaseAndTheRefillNeverBothHold() {
        for (int gas = 0; gas <= 1000; gas += 25) {
            for (int minerals = 0; minerals <= 1000; minerals += 25) {
                for (int gasDemand = 0; gasDemand <= 600; gasDemand += 50) {
                    boolean idle = GasBalance.isGasIdle(gas, minerals, gasDemand);
                    boolean wants = GasBalance.wantsGasWorkers(gas, minerals, gasDemand);

                    assertFalse(idle && wants);
                }
            }
        }
    }

    @Test
    void atMostOneWorkerIsReleasedPerInterval() {
        int gasWorkers = 3;
        int lastRelease = 0;
        List<Integer> releaseFrames = new ArrayList<>();

        for (int frame = 1; frame <= 1000; frame++) {
            if (!GasBalance.isGasIdle(500, 50, 0) || gasWorkers == 0) {
                continue;
            }
            if (GasBalance.isReleaseDue(frame, lastRelease)) {
                gasWorkers--;
                lastRelease = frame;
                releaseFrames.add(frame);
            }
        }

        assertEquals(3, releaseFrames.size());
        for (int i = 1; i < releaseFrames.size(); i++) {
            assertTrue(releaseFrames.get(i) - releaseFrames.get(i - 1) >= INTERVAL);
        }
    }

    @Test
    void aReleaseIsNotDueInsideTheInterval() {
        int lastRelease = 1000;

        assertFalse(GasBalance.isReleaseDue(lastRelease + INTERVAL - 1, lastRelease));
        assertTrue(GasBalance.isReleaseDue(lastRelease + INTERVAL, lastRelease));
    }
}
