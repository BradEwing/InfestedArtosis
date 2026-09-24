package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WalkPosition;
import org.junit.jupiter.api.Test;
import util.Arc;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentArcSpacingTest {

    private static final Position CHOKE = new Position(2048, 2048);
    private static final int MAP_PIXELS = 4096;
    private static final Set<WalkPosition> ALL_WALKABLE = Collections.emptySet();
    private static final List<UnitType> LINGS = Collections.singletonList(UnitType.Zerg_Zergling);
    private static final int LING_WIDTH = 15;
    private static final int[] PUSHBACK_RADII = {0, 160, 192, 288, ContainmentPushback.MAX_RADIUS};

    private static Arc spacedArc(int pushbackRadius, int points, int spacing, int facingDegrees) {
        int radius = SquadManager.containmentRadius(pushbackRadius, points, spacing);
        int degrees = SquadManager.containmentDegrees(radius, points, spacing);
        double facing = Math.toRadians(facingDegrees);
        Position faceTarget = new Position(CHOKE.getX() + (int) (Math.cos(facing) * 640),
                CHOKE.getY() + (int) (Math.sin(facing) * 640));
        Arc arc = new Arc(CHOKE, faceTarget, radius, degrees, points);
        arc.compute(ALL_WALKABLE, Collections.emptyList(), 0, MAP_PIXELS, MAP_PIXELS);
        return arc;
    }

    private static double smallestGap(Arc arc) {
        List<Position> points = arc.getPositions();
        double smallest = Double.MAX_VALUE;
        for (int i = 1; i < points.size(); i++) {
            smallest = Math.min(smallest, points.get(i - 1).getDistance(points.get(i)));
        }
        return smallest;
    }

    @Test
    void aZerglingIsFifteenPixelsWide() {
        assertEquals(LING_WIDTH, SquadManager.containmentSpacing(LINGS));
    }

    @Test
    void theWidestMemberSetsTheSpacing() {
        List<UnitType> mixed = Arrays.asList(UnitType.Zerg_Zergling, UnitType.Zerg_Ultralisk);
        int ultraliskWidth = UnitType.Zerg_Ultralisk.dimensionLeft() + UnitType.Zerg_Ultralisk.dimensionRight();

        assertTrue(ultraliskWidth > LING_WIDTH);
        assertEquals(ultraliskWidth, SquadManager.containmentSpacing(mixed));
        assertEquals(0, SquadManager.containmentSpacing(Collections.emptyList()));
    }

    @Test
    void consecutivePointsAreAtLeastOneLingWidthApartForFourToFortyLings() {
        int spacing = SquadManager.containmentSpacing(LINGS);
        for (int lings = 4; lings <= 40; lings++) {
            for (int facing = 0; facing < 360; facing++) {
                Arc arc = spacedArc(0, lings, spacing, facing);

                assertEquals(lings, arc.size(), lings + " lings facing " + facing);
                assertTrue(smallestGap(arc) >= LING_WIDTH,
                        lings + " lings facing " + facing + " leave a gap of " + smallestGap(arc));
            }
        }
    }

    @Test
    void theRadiusNeverDropsBelowThePushbackRadiusAndTheSpacingHoldsThere() {
        int spacing = SquadManager.containmentSpacing(LINGS);
        for (int pushback : PUSHBACK_RADII) {
            for (int lings = 4; lings <= 40; lings++) {
                int radius = SquadManager.containmentRadius(pushback, lings, spacing);
                assertTrue(radius >= pushback, lings + " lings at pushback " + pushback + " drew " + radius);

                Arc arc = spacedArc(pushback, lings, spacing, 90);
                assertTrue(smallestGap(arc) >= LING_WIDTH,
                        lings + " lings at pushback " + pushback + " leave a gap of " + smallestGap(arc));
            }
        }
    }

    @Test
    void aSmallSquadKeepsTheDefaultArc() {
        int spacing = SquadManager.containmentSpacing(LINGS);
        for (int lings = 4; lings <= 8; lings++) {
            assertEquals(160, SquadManager.containmentRadius(0, lings, spacing));
            assertEquals(90, SquadManager.containmentDegrees(160, lings, spacing));
        }
    }

    @Test
    void thirtyFourLingsGrowTheRadiusWithoutWideningTheSpan() {
        int spacing = SquadManager.containmentSpacing(LINGS);
        int radius = SquadManager.containmentRadius(0, 34, spacing);

        assertTrue(radius > 160 && radius < ContainmentPushback.MAX_RADIUS, "radius " + radius);
        assertEquals(90, SquadManager.containmentDegrees(radius, 34, spacing));
    }

    @Test
    void aSquadTooLargeForTheRadiusCapWidensTheSpanInstead() {
        int spacing = SquadManager.containmentSpacing(LINGS);
        int radius = SquadManager.containmentRadius(0, 60, spacing);
        int degrees = SquadManager.containmentDegrees(radius, 60, spacing);

        assertEquals(ContainmentPushback.MAX_RADIUS - ContainmentPushback.RADIUS_STEP, radius);
        assertTrue(degrees > 90 && degrees <= 180, "degrees " + degrees);
        assertTrue(smallestGap(spacedArc(0, 60, spacing, 45)) >= LING_WIDTH);
    }

    @Test
    void anArcSizedForItsSquadAlwaysLeavesOnePushbackStep() {
        int spacing = SquadManager.containmentSpacing(LINGS);
        for (int lings = 4; lings <= 100; lings++) {
            int radius = SquadManager.containmentRadius(0, lings, spacing);
            assertTrue(radius + ContainmentPushback.RADIUS_STEP <= ContainmentPushback.MAX_RADIUS,
                    lings + " lings drew " + radius);
        }
    }

    @Test
    void fortyLingsHitFromOutOfReachCanStillBePushedBack() {
        int spacing = SquadManager.containmentSpacing(LINGS);
        Arc held = spacedArc(0, 40, spacing, 270);
        List<StaticDefenseZone> zones = Collections.singletonList(
                new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode, CHOKE, held.getRadius()));

        Arc pushed = ContainmentPushback.pushBack(held, zones, 0, ALL_WALKABLE, MAP_PIXELS, MAP_PIXELS);

        assertNotNull(pushed);
        assertEquals(held.getRadius() + ContainmentPushback.RADIUS_STEP, pushed.getRadius());
        assertEquals(40, pushed.size());
        assertTrue(smallestGap(pushed) >= LING_WIDTH);
    }

    @Test
    void aPushedBackArcKeepsItsWidenedSpan() {
        Arc wide = new Arc(CHOKE, new Position(2048, 1408), 256, 120, 40);

        assertEquals(120, wide.withRadius(288).getArcDegrees());
    }

    @Test
    void anEmptySquadAsksForNoSpacing() {
        assertEquals(0, Arc.radiusForSpacing(40, 90, 0));
        assertEquals(0, Arc.degreesForSpacing(40, 160, 0));
        assertEquals(160, SquadManager.containmentRadius(0, 4, 0));
        assertEquals(90, SquadManager.containmentDegrees(160, 4, 0));
    }
}
