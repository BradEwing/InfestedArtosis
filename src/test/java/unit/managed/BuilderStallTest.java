package unit.managed;

import bwapi.Position;
import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderStallTest {
    private static final Position SITE = new Position(3000, 3000);
    private static final double DRONE_SPEED = 4.9;
    private static final String WALL_FIELD = "wall field";

    @Test
    void aBuilderClosingPastAWallNeverStalls() {
        BuilderStall<String> stall = new BuilderStall<>();
        for (int frame = 0; 2000 - DRONE_SPEED * frame > BuilderStall.ARRIVAL_DISTANCE; frame++) {
            assertFalse(stall.isStalled(SITE, 2000 - DRONE_SPEED * frame, false, frame));
        }
    }

    @Test
    void aBuilderSampledAtBuildCadenceClosingPastAWallNeverStalls() {
        BuilderStall<String> stall = new BuilderStall<>();
        for (int frame = 0; 2000 - DRONE_SPEED * frame > BuilderStall.ARRIVAL_DISTANCE; frame += 11) {
            assertFalse(stall.isStalled(SITE, 2000 - DRONE_SPEED * frame, false, frame));
        }
    }

    @Test
    void aBuilderHeldAtAConstantDistanceStalls() {
        BuilderStall<String> stall = new BuilderStall<>();
        for (int frame = 0; frame < BuilderStall.STALL_FRAMES; frame++) {
            assertFalse(stall.isStalled(SITE, 600, false, frame));
        }
        assertTrue(stall.isStalled(SITE, 600, false, BuilderStall.STALL_FRAMES));
    }

    @Test
    void aBuilderCreepingUnderTheProgressBarStalls() {
        BuilderStall<String> stall = new BuilderStall<>();
        for (int frame = 0; frame < BuilderStall.STALL_FRAMES; frame++) {
            assertFalse(stall.isStalled(SITE, 600 - frame * 0.25, false, frame));
        }
        assertTrue(stall.isStalled(SITE, 600 - BuilderStall.STALL_FRAMES * 0.25, false, BuilderStall.STALL_FRAMES));
    }

    @Test
    void aBuilderWaitingWithinArrivalDistanceNeverStalls() {
        BuilderStall<String> stall = new BuilderStall<>();
        for (int frame = 0; frame < 1000; frame++) {
            assertFalse(stall.isStalled(SITE, BuilderStall.ARRIVAL_DISTANCE, false, frame));
        }
    }

    @Test
    void aHarvestingBuilderNeverStalls() {
        BuilderStall<String> stall = new BuilderStall<>();
        for (int frame = 0; frame < 1000; frame++) {
            assertFalse(stall.isStalled(SITE, 600, true, frame));
        }
    }

    @Test
    void harvestingRestartsTheStallWindow() {
        BuilderStall<String> stall = new BuilderStall<>();
        stall.isStalled(SITE, 600, false, 0);
        stall.isStalled(SITE, 600, true, 100);
        assertFalse(stall.isStalled(SITE, 600, false, 101));
        assertFalse(stall.isStalled(SITE, 600, false, 101 + BuilderStall.STALL_FRAMES - 1));
        assertTrue(stall.isStalled(SITE, 600, false, 101 + BuilderStall.STALL_FRAMES));
    }

    @Test
    void aNewMoveTargetRestartsTheStallWindow() {
        BuilderStall<String> stall = new BuilderStall<>();
        stall.isStalled(SITE, 600, false, 0);
        Position moved = new Position(1000, 1000);
        assertFalse(stall.isStalled(moved, 900, false, 100));
        assertFalse(stall.isStalled(moved, 900, false, 100 + BuilderStall.STALL_FRAMES - 1));
        assertTrue(stall.isStalled(moved, 900, false, 100 + BuilderStall.STALL_FRAMES));
    }

    @Test
    void aStalledBackBaseBuilderSearches256PixelsAroundItself() {
        BuilderStall<String> stall = new BuilderStall<>();
        RecordingLookup lookup = new RecordingLookup(WALL_FIELD, 250);
        Position drone = new Position(1200, 1200);
        for (int frame = 0; frame < BuilderStall.STALL_FRAMES; frame++) {
            assertNull(stall.divertIfStalled(drone, SITE, 900, false, frame, lookup));
        }
        assertTrue(lookup.positions.isEmpty());

        assertEquals(WALL_FIELD, stall.divertIfStalled(drone, SITE, 900, false, BuilderStall.STALL_FRAMES, lookup));

        assertEquals(Collections.singletonList(drone), lookup.positions);
        assertEquals(Collections.singletonList(256), lookup.radii);
        assertEquals(WALL_FIELD, stall.getBlocker());
    }

    @Test
    void aBuilderClosingPastAWallNeverSearchesForIt() {
        BuilderStall<String> stall = new BuilderStall<>();
        RecordingLookup lookup = new RecordingLookup(WALL_FIELD);
        Position drone = new Position(1200, 1200);
        for (int frame = 0; 2000 - DRONE_SPEED * frame > BuilderStall.ARRIVAL_DISTANCE; frame++) {
            assertNull(stall.divertIfStalled(drone, SITE, 2000 - DRONE_SPEED * frame, false, frame, lookup));
        }
        assertTrue(lookup.positions.isEmpty());
        assertNull(stall.getBlocker());
    }

    @Test
    void aStalledBuilderWithNoBlockerInRangeKeepsSearching() {
        BuilderStall<String> stall = new BuilderStall<>();
        RecordingLookup lookup = new RecordingLookup(null);
        Position drone = new Position(1200, 1200);
        for (int frame = 0; frame <= BuilderStall.STALL_FRAMES + 1; frame++) {
            assertNull(stall.divertIfStalled(drone, SITE, 600, false, frame, lookup));
        }
        assertEquals(2, lookup.positions.size());
        assertNull(stall.getBlocker());
    }

    @Test
    void clearingTheBlockerRestartsTheStallWindow() {
        BuilderStall<String> stall = new BuilderStall<>();
        stall.isStalled(SITE, 600, false, 0);
        stall.clearBlocker();
        assertFalse(stall.isStalled(SITE, 600, false, 200));
        assertFalse(stall.isStalled(SITE, 600, false, 200 + BuilderStall.STALL_FRAMES - 1));
        assertTrue(stall.isStalled(SITE, 600, false, 200 + BuilderStall.STALL_FRAMES));
    }

    @Test
    void aNewPlanClearsTheProgressHistory() {
        BuilderStall<String> stall = new BuilderStall<>();
        Plan first = new BuildingPlan(UnitType.Zerg_Hatchery, 1);
        Plan second = new BuildingPlan(UnitType.Zerg_Hatchery, 1);
        stall.onPlanChange(null, first);
        for (int frame = 0; frame <= 100; frame++) {
            assertFalse(stall.isStalled(SITE, 600, false, frame));
        }

        stall.onPlanChange(first, second);

        for (int frame = 101; frame < 101 + BuilderStall.STALL_FRAMES; frame++) {
            assertFalse(stall.isStalled(SITE, 600, false, frame));
        }
        assertTrue(stall.isStalled(SITE, 600, false, 101 + BuilderStall.STALL_FRAMES));
    }

    @Test
    void aPlanChangeClearsTheBlocker() {
        BuilderStall<String> stall = new BuilderStall<>();
        Plan first = new BuildingPlan(UnitType.Zerg_Hatchery, 1);
        Plan second = new BuildingPlan(UnitType.Zerg_Hatchery, 1);
        stall.onPlanChange(null, first);
        stall.divertTo(WALL_FIELD);

        stall.onPlanChange(first, second);

        assertNull(stall.getBlocker());
    }

    @Test
    void anEvictionClearsTheBlocker() {
        BuilderStall<String> stall = new BuilderStall<>();
        Plan plan = new BuildingPlan(UnitType.Zerg_Hatchery, 1);
        stall.onPlanChange(null, plan);
        stall.divertTo(WALL_FIELD);

        stall.onPlanChange(plan, null);

        assertNull(stall.getBlocker());
    }

    @Test
    void reassigningTheSamePlanKeepsTheBlocker() {
        BuilderStall<String> stall = new BuilderStall<>();
        Plan plan = new BuildingPlan(UnitType.Zerg_Hatchery, 1);
        stall.onPlanChange(null, plan);
        stall.divertTo(WALL_FIELD);

        stall.onPlanChange(plan, plan);

        assertEquals(WALL_FIELD, stall.getBlocker());
    }

    private static final class RecordingLookup implements BiFunction<Position, Integer, String> {
        private final String blocker;
        private final int blockerDistance;
        private final List<Position> positions = new ArrayList<>();
        private final List<Integer> radii = new ArrayList<>();

        RecordingLookup(String blocker) {
            this(blocker, 0);
        }

        RecordingLookup(String blocker, int blockerDistance) {
            this.blocker = blocker;
            this.blockerDistance = blockerDistance;
        }

        @Override
        public String apply(Position position, Integer radius) {
            positions.add(position);
            radii.add(radius);
            return radius >= blockerDistance ? blocker : null;
        }
    }
}
