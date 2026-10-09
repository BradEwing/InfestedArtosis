package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirHarassStateTest {

    private static ExposedTargets.Group group(Position anchor) {
        return new ExposedTargets.Group(anchor, 1, 1, Collections.emptySet());
    }

    @Test
    void anExposedTargetIsAimedAtItsAnchorAndStartsTransit() {
        AirHarassState state = new AirHarassState(12000, 1080);
        Position anchor = new Position(2000, 2000);
        state.arrive(12100);

        state.targetExposed(group(anchor), 12200);

        assertTrue(state.hasTarget());
        assertTrue(state.targetsExposed());
        assertEquals(anchor, state.targetCenter());
        assertEquals(anchor, state.getStrikePoint());
        assertEquals(AirHarassState.Phase.TRANSIT, state.getPhase());
        assertFalse(state.hasArrived());
        assertEquals(12200, state.getLastProgressFrame());
    }

    @Test
    void aBaseTargetClearsTheExposedAnchor() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.targetExposed(group(new Position(2000, 2000)), 12000);

        state.target(null, new Position(400, 3700), 12100);

        assertFalse(state.targetsExposed());
        assertNull(state.getExposedAnchor());
        assertNull(state.getExposedGroup());
        assertFalse(state.hasTarget());
    }

    @Test
    void followingAnExposedTargetReplacesItsGroupAndAnchorButNotItsStrikePoint() {
        AirHarassState state = new AirHarassState(12000, 1080);
        Position start = new Position(2000, 2000);
        state.targetExposed(group(start), 12000);
        ExposedTargets.Group moved = group(new Position(2100, 2000));

        state.follow(moved);

        assertSame(moved, state.getExposedGroup());
        assertEquals(moved.getAnchor(), state.getExposedAnchor());
        assertEquals(start, state.getStrikePoint());
        assertTrue(state.targetsExposed());
    }

    @Test
    void anEdgeTurretIsCountedOnceNoMatterHowManyMutasTakeItOn() {
        AirHarassState state = new AirHarassState(100, 600);

        assertTrue(state.engageEdgeTurret(7));
        assertFalse(state.engageEdgeTurret(7));
        assertTrue(state.engageEdgeTurret(8));
        assertEquals(2, state.edgeTurretsEngaged());
    }

    @Test
    void aVolleyCommittedToATargetIsRecordedOnceAndResolvedWhenItDies() {
        AirHarassState state = new AirHarassState(12000, 1080);

        assertTrue(state.noteSnipe(7, bwapi.UnitType.Terran_SCV, 60, 72, 12010));
        assertFalse(state.noteSnipe(7, bwapi.UnitType.Terran_SCV, 40, 72, 12020));

        AirHarassState.Snipe snipe = state.resolveSnipe(7);
        assertEquals(60, snipe.getHitPoints());
        assertEquals(72, snipe.getAlpha());
        assertEquals(12010, snipe.getFrame());
        assertNull(state.resolveSnipe(7));
    }

    @Test
    void aTargetStillStandingAWindowAfterItsVolleyWasCommittedCountsAsMissed() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.noteSnipe(1, bwapi.UnitType.Terran_SCV, 60, 72, 12000);
        state.noteSnipe(2, bwapi.UnitType.Terran_Marine, 40, 72, 12100);

        assertEquals(1, state.missedSnipes(12121, 120).size());
        assertEquals(0, state.missedSnipes(12121, 120).size());
        assertEquals(1, state.drainSnipes().size());
        assertTrue(state.drainSnipes().isEmpty());
    }

    @Test
    void aPricingIsNewOnlyWhenItsKeyChangesAndANewTargetForgetsIt() {
        AirHarassState state = new AirHarassState(12000, 1080);

        assertTrue(state.notePricing("ENTER/CLEAR"));
        assertFalse(state.notePricing("ENTER/CLEAR"));
        assertTrue(state.notePricing("REROUTE/DETOUR"));
        state.target(null, new Position(400, 3700), 12100);
        assertTrue(state.notePricing("REROUTE/DETOUR"));
    }

    @Test
    void theApproachZonesAreACopyAndANewTargetClearsThem() {
        AirHarassState state = new AirHarassState(12000, 1080);
        AirHarassTargeting.AirThreat goliath = AirHarassTargeting.AirThreat.of(1, bwapi.UnitType.Terran_Goliath,
                new Position(1000, 1000), 160);
        state.setApproachZones(Collections.singletonList(goliath));

        state.approachZones().clear();
        assertEquals(1, state.approachZones().size());
        state.targetExposed(group(new Position(2000, 2000)), 12100);
        assertTrue(state.approachZones().isEmpty());
    }

    @Test
    void aTargetSettledByAKillOrAMissIsNeverRecordedAgainInTheSameHarass() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.noteSnipe(1, bwapi.UnitType.Terran_SCV, 60, 72, 12000);
        state.noteSnipe(2, bwapi.UnitType.Terran_SCV, 60, 72, 12000);
        state.noteSnipe(3, bwapi.UnitType.Terran_SCV, 60, 72, 12000);

        state.resolveSnipe(1);
        state.missedSnipes(12200, 120);
        assertTrue(state.drainSnipes().isEmpty());

        assertFalse(state.noteSnipe(1, bwapi.UnitType.Terran_SCV, 60, 72, 12300));
        assertFalse(state.noteSnipe(2, bwapi.UnitType.Terran_SCV, 60, 72, 12300));
        assertFalse(state.noteSnipe(3, bwapi.UnitType.Terran_SCV, 60, 72, 12300));
    }

    @Test
    void aFlightThatClosesNoDistanceForAWindowIsStalled() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.target(null, new Position(400, 3700), 12000);

        assertFalse(state.transitStalled(3000, 12010, 480, 64));
        assertFalse(state.transitStalled(2900, 12400, 480, 64));
        assertFalse(state.transitStalled(2890, 12800, 480, 64));
        assertTrue(state.transitStalled(2880, 12880, 480, 64));
    }

    @Test
    void aNewTargetRestartsTheStallCount() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.target(null, new Position(400, 3700), 12000);
        state.transitStalled(3000, 12010, 480, 64);

        state.target(null, new Position(900, 3700), 12700);

        assertFalse(state.transitStalled(3000, 12710, 480, 64));
    }
}
