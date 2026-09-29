package unit.squad;

import bwapi.Position;
import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link EndgameHunt#update} and {@link EndgameHunt#huntPosition} over real ObservedUnits wrapping real
 * JBWAPI units, with visibility and lift read through the test seams rather than a live game.
 */
class EndgameHuntTrackingTest {

    private static final Position SQUAD = new Position(1000, 1000);
    private static final Position NEAR = new Position(1100, 1000);
    private static final Position FAR = new Position(2000, 1000);
    private static final int LATE = new Time(20, 0).getFrames();
    private static final int WINNING_SUPPLY = 290;
    private static final Function<ObservedUnit, Position> LAST_KNOWN = ObservedUnit::getLastKnownLocation;

    private TestUnits units;
    private EndgameHunt hunt;
    private Set<Unit> visibleLifted;
    private Set<Unit> visibleGrounded;

    @BeforeEach
    void setUp() {
        units = new TestUnits();
        hunt = new EndgameHunt();
        visibleLifted = new HashSet<>();
        visibleGrounded = new HashSet<>();
    }

    @Test
    void assimilatorRebuiltOnItsGeyserIsHunted() {
        Unit geyser = units.unit(UnitType.Protoss_Assimilator, 61);
        ObservedUnitTracker tracker = new ObservedUnitTracker();
        tracker.onUnitShow(geyser, 6921, false);
        tracker.onUnitDestroy(geyser, 8318);
        tracker.onUnitShow(geyser, 18874, false);
        Collection<ObservedUnit> living = tracker.getLivingObservedUnits();
        living.forEach(observed -> observed.setLastKnownLocation(FAR));

        update(living, LATE, WINNING_SUPPLY);

        assertEquals(FAR, hunt.huntPosition(SQUAD, living, UnitType.Zerg_Zergling, LAST_KNOWN));
        assertFalse(hunt.isHuntingFlyingBuildings());
    }

    @Test
    void liftedBuildingIsHuntedByAntiAir() {
        ObservedUnit barracks = observed(UnitType.Terran_Barracks, NEAR);
        visibleLifted.add(barracks.getUnit());
        List<ObservedUnit> living = Arrays.asList(barracks);

        update(living, LATE, WINNING_SUPPLY);

        assertTrue(hunt.isLifted(barracks.getUnit()));
        assertEquals(NEAR, hunt.huntPosition(SQUAD, living, UnitType.Zerg_Hydralisk, LAST_KNOWN));
        assertEquals(NEAR, hunt.huntPosition(SQUAD, living, UnitType.Zerg_Mutalisk, LAST_KNOWN));
    }

    @Test
    void groundAttackerPrefersTheGroundedBuildingItCanHit() {
        ObservedUnit barracks = observed(UnitType.Terran_Barracks, NEAR);
        ObservedUnit refinery = observed(UnitType.Terran_Refinery, FAR);
        visibleLifted.add(barracks.getUnit());
        visibleGrounded.add(refinery.getUnit());
        List<ObservedUnit> living = Arrays.asList(barracks, refinery);

        update(living, LATE, WINNING_SUPPLY);

        assertEquals(FAR, hunt.huntPosition(SQUAD, living, UnitType.Zerg_Zergling, LAST_KNOWN));
        assertEquals(NEAR, hunt.huntPosition(SQUAD, living, UnitType.Zerg_Hydralisk, LAST_KNOWN));
    }

    @Test
    void liftIsRememberedOnceTheBuildingIsOutOfSight() {
        ObservedUnit barracks = observed(UnitType.Terran_Barracks, NEAR);
        visibleLifted.add(barracks.getUnit());
        List<ObservedUnit> living = Arrays.asList(barracks);
        update(living, LATE, WINNING_SUPPLY);

        visibleLifted.clear();
        update(living, LATE + 1, WINNING_SUPPLY);

        assertTrue(hunt.isLifted(barracks.getUnit()));
        assertTrue(hunt.isHuntingFlyingBuildings());
    }

    @Test
    void landedBuildingEndsTheFlyingBuildingHunt() {
        ObservedUnit barracks = observed(UnitType.Terran_Barracks, NEAR);
        visibleLifted.add(barracks.getUnit());
        List<ObservedUnit> living = Arrays.asList(barracks);
        update(living, LATE, WINNING_SUPPLY);

        visibleLifted.clear();
        visibleGrounded.add(barracks.getUnit());
        update(living, LATE + 1, WINNING_SUPPLY);

        assertFalse(hunt.isLifted(barracks.getUnit()));
        assertFalse(hunt.isHuntingFlyingBuildings());
    }

    @Test
    void antiAirGateOpensOnlyWhenLateAheadAndOnlyLiftedBuildingsRemain() {
        ObservedUnit barracks = observed(UnitType.Terran_Barracks, NEAR);
        ObservedUnit engineeringBay = observed(UnitType.Terran_Engineering_Bay, FAR);
        visibleLifted.add(barracks.getUnit());
        visibleLifted.add(engineeringBay.getUnit());
        List<ObservedUnit> living = new ArrayList<>(Arrays.asList(barracks, engineeringBay));

        update(living, LATE, WINNING_SUPPLY);
        assertTrue(hunt.isHuntingFlyingBuildings());

        update(living, EndgameHunt.MIN_ANTI_AIR_TIME.getFrames() - 1, WINNING_SUPPLY);
        assertFalse(hunt.isHuntingFlyingBuildings());

        update(living, LATE, EndgameHunt.MIN_OUR_SUPPLY_USED - 1);
        assertFalse(hunt.isHuntingFlyingBuildings());

        List<ObservedUnit> withScv = new ArrayList<>(living);
        withScv.add(observed(UnitType.Terran_SCV, FAR));
        update(withScv, LATE, WINNING_SUPPLY);
        assertFalse(hunt.isHuntingFlyingBuildings());

        List<ObservedUnit> withDepot = new ArrayList<>(living);
        withDepot.add(observed(UnitType.Terran_Supply_Depot, FAR));
        update(withDepot, LATE, WINNING_SUPPLY);
        assertFalse(hunt.isHuntingFlyingBuildings());
    }

    @Test
    void neverSeenBuildingCountsAsGrounded() {
        ObservedUnit barracks = observed(UnitType.Terran_Barracks, NEAR);

        update(Arrays.asList(barracks), LATE, WINNING_SUPPLY);

        assertFalse(hunt.isLifted(barracks.getUnit()));
        assertFalse(hunt.isHuntingFlyingBuildings());
    }

    private ObservedUnit observed(UnitType type, Position lastKnown) {
        ObservedUnit observed = new ObservedUnit(units.unit(type), new Time(0), false);
        observed.setLastKnownLocation(lastKnown);
        return observed;
    }

    private void update(Collection<ObservedUnit> living, int frame, int ourSupplyUsed) {
        hunt.update(living, frame, ourSupplyUsed, unit -> {
            if (visibleLifted.contains(unit)) {
                return true;
            }
            return visibleGrounded.contains(unit) ? false : null;
        });
    }
}
