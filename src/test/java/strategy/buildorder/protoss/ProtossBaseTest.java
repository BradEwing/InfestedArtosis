package strategy.buildorder.protoss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtossBaseTest {

    @Test
    void neverExceedsTheCapAcrossEveryZealotCount() {
        for (int zealots = 0; zealots <= 60; zealots++) {
            for (int gateways = 0; gateways <= 12; gateways++) {
                int demand = ProtossBase.zealotDrivenZerglings(zealots, gateways);
                assertTrue(demand <= ProtossBase.MAX_ZEALOT_DEMAND,
                        "zealots=" + zealots + " gateways=" + gateways + " demanded " + demand);
            }
        }
    }

    @Test
    void scalesWithZealotsBelowTheCapWhileNoGatewayIsObserved() {
        assertEquals(0, ProtossBase.zealotDrivenZerglings(0, 0));
        assertEquals(8, ProtossBase.zealotDrivenZerglings(4, 0));
        assertEquals(24, ProtossBase.zealotDrivenZerglings(12, 0));
    }

    @Test
    void capsTheUnscoutedDemandAtTwentyFive() {
        assertEquals(ProtossBase.MAX_ZEALOT_DEMAND, ProtossBase.zealotDrivenZerglings(13, 0));
        assertEquals(ProtossBase.MAX_ZEALOT_DEMAND, ProtossBase.zealotDrivenZerglings(32, 0));
        assertEquals(ProtossBase.MAX_ZEALOT_DEMAND, ProtossBase.zealotDrivenZerglings(55, 0));
    }

    @Test
    void tracksGatewayCountRatherThanZealotCountOnceGatewaysAreObserved() {
        assertEquals(6, ProtossBase.zealotDrivenZerglings(60, 1));
        assertEquals(12, ProtossBase.zealotDrivenZerglings(60, 2));
        assertEquals(18, ProtossBase.zealotDrivenZerglings(60, 3));
        assertEquals(24, ProtossBase.zealotDrivenZerglings(60, 4));
    }

    @Test
    void holdsTheOverallCapWhenGatewaysAreNumerous() {
        assertEquals(ProtossBase.MAX_ZEALOT_DEMAND, ProtossBase.zealotDrivenZerglings(60, 5));
        assertEquals(ProtossBase.MAX_ZEALOT_DEMAND, ProtossBase.zealotDrivenZerglings(60, 12));
    }

    @Test
    void keepsGatewaysAsACapNotAFloor() {
        assertEquals(4, ProtossBase.zealotDrivenZerglings(2, 8));
        assertEquals(0, ProtossBase.zealotDrivenZerglings(0, 8));
    }

    @Test
    void bindsWuliBotsMedianZealotCountToItsGatewayCount() {
        assertEquals(18, ProtossBase.zealotDrivenZerglings(31, 3));
    }
}
