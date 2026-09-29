package unit.squad.horizon;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerPricingTest {

    private static final Map<UnitSizeType, Double> ALL_SMALL = Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final Position BUNKER = new Position(1000, 1000);
    private static final Position SECOND_BUNKER = new Position(1200, 1000);
    private static final int FULL = HorizonCombatSimulator.BUNKER_MAX_GARRISON;
    private static final int KNOWN_MARINES = 4;
    private static final int AIR_REACH = BunkerPricing.reach(true, 0);
    private static final int GROUND_REACH = BunkerPricing.reach(false, 0);
    private static final int CENTROID_DISTANCE_THAT_FLIPPED = 294;
    private static final int TRUST_FRAMES = 48;
    private static final int RETREATED_CENTROID_DISTANCE = 481;
    private static final int FLIPPED_BACK_CENTROID_DISTANCE = 219;
    private static final Position BUILDING_BEHIND_THE_BUNKER = new Position(700, 1000);

    private static Position rightOfBunker(Position bunker, int gap) {
        return new Position(bunker.getX() + UnitType.Terran_Bunker.dimensionRight() + gap, bunker.getY());
    }

    private static List<BunkerPricing.Leg> standingAt(Position... positions) {
        List<BunkerPricing.Leg> legs = new ArrayList<>();
        for (Position position : positions) {
            legs.add(new BunkerPricing.Leg(position, position));
        }
        return legs;
    }

    private static BunkerPricing.Candidate unmeasured(Position bunker, double fireWeight) {
        return new BunkerPricing.Candidate(bunker, true, fireWeight, FULL, false,
                HorizonCombatSimulator.weightedGroundStrength(UnitType.Terran_Bunker, ALL_SMALL),
                HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Terran_Bunker, ALL_SMALL));
    }

    private static double pricedWeight(Position bunker, int reach, List<BunkerPricing.Leg> legs) {
        return BunkerPricing.fireWeight(BunkerPricing.nearestGap(bunker, legs), reach);
    }

    private static double totalOccupants(List<BunkerPricing.Candidate> candidates) {
        double total = 0;
        for (BunkerPricing.Candidate candidate : candidates) {
            total += candidate.getOccupants();
        }
        return total;
    }

    private static List<ObservedUnit> marines(int count) {
        List<ObservedUnit> living = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            living.add(ObservedUnitFixture.observedUnit(UnitType.Terran_Marine, new Time(0)));
        }
        return living;
    }

    @Test
    void aBunkerWhoseReachDoesNotTouchTheSquadAddsNothing() {
        List<BunkerPricing.Leg> flock = standingAt(new Position(BUNKER.getX() + CENTROID_DISTANCE_THAT_FLIPPED,
                BUNKER.getY()));

        assertEquals(0.0, pricedWeight(BUNKER, AIR_REACH, flock));
    }

    @Test
    void aBunkerJustPastTheFalloffAddsNothingForAGroundSquad() {
        List<BunkerPricing.Leg> lings = standingAt(rightOfBunker(BUNKER,
                GROUND_REACH + BunkerPricing.APPROACH_FALLOFF + 1));

        assertEquals(0.0, pricedWeight(BUNKER, GROUND_REACH, lings));
    }

    @Test
    void aBunkerInReachIsStillPriced() {
        List<BunkerPricing.Leg> flock = standingAt(rightOfBunker(BUNKER, AIR_REACH));
        BunkerPricing.Candidate candidate = unmeasured(BUNKER, pricedWeight(BUNKER, AIR_REACH, flock));

        BunkerPricing.allocate(Collections.singletonList(candidate), KNOWN_MARINES);

        assertEquals(FULL, candidate.getOccupants(), 1e-9);
        assertEquals(HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Terran_Bunker, ALL_SMALL),
                candidate.antiAir(), 1e-9);
        assertTrue(candidate.ground() > 0);
    }

    @Test
    void aBunkerOnTheSquadsPathIsPricedWhileTheSquadStandsOutOfReach() {
        Position member = new Position(BUNKER.getX() + CENTROID_DISTANCE_THAT_FLIPPED, BUNKER.getY());
        Position targetBesideTheBunker = rightOfBunker(BUNKER, AIR_REACH / 2);
        List<BunkerPricing.Leg> legs = new ArrayList<>(standingAt(member));
        legs.add(new BunkerPricing.Leg(member, targetBesideTheBunker));

        assertEquals(1.0, pricedWeight(BUNKER, AIR_REACH, legs));
    }

    @Test
    void aPathThatPassesTheBunkerByIsMeasuredAtItsNearestPoint() {
        Position from = new Position(BUNKER.getX() - 400, BUNKER.getY() + 100);
        Position to = new Position(BUNKER.getX() + 400, BUNKER.getY() + 100);
        List<BunkerPricing.Leg> legs = Collections.singletonList(new BunkerPricing.Leg(from, to));

        assertEquals(100 - UnitType.Terran_Bunker.dimensionDown(), BunkerPricing.nearestGap(BUNKER, legs), 1e-9);
    }

    @Test
    void theWeightFallsOffLinearlyPastTheReach() {
        assertEquals(1.0, BunkerPricing.fireWeight(AIR_REACH, AIR_REACH));
        assertEquals(0.5, BunkerPricing.fireWeight(AIR_REACH + BunkerPricing.APPROACH_FALLOFF / 2.0, AIR_REACH), 1e-9);
        assertEquals(0.0, BunkerPricing.fireWeight(AIR_REACH + BunkerPricing.APPROACH_FALLOFF, AIR_REACH));
    }

    @Test
    void anAirSquadReadsTheMarinesAirRangePlusTheBunkerAllowance() {
        int expected = UnitType.Terran_Marine.airWeapon().maxRange() + EnemyReachMemory.BUNKER_ALLOWANCE;

        assertEquals(expected, AIR_REACH);
        assertEquals(expected + 10, BunkerPricing.reach(true, expected + 10));
        assertEquals(HorizonCombatSimulator.MAX_POSITIONAL_REACH, BunkerPricing.reach(true, 1000));
    }

    @Test
    void aGroundSquadReadsThePositionalReach() {
        assertEquals(HorizonCombatSimulator.positionalReach(UnitType.Terran_Bunker, 191),
                BunkerPricing.reach(false, 191));
        assertEquals(EnemyReachMemory.baseGroundRange(UnitType.Terran_Bunker), GROUND_REACH);
    }

    @Test
    void twoUnseenBunkersAgainstFourKnownMarinesPriceAtMostFourMarines() {
        List<BunkerPricing.Candidate> bunkers = Arrays.asList(unmeasured(BUNKER, 1.0), unmeasured(SECOND_BUNKER, 1.0));
        int pool = BunkerPricing.garrisonPool(marines(KNOWN_MARINES), 0);

        BunkerPricing.allocate(bunkers, pool);

        assertEquals(KNOWN_MARINES, pool);
        assertTrue(totalOccupants(bunkers) <= KNOWN_MARINES + 1e-9);
        assertEquals(KNOWN_MARINES / 2.0, bunkers.get(0).getOccupants(), 1e-9);
        assertEquals(KNOWN_MARINES / 2.0, bunkers.get(1).getOccupants(), 1e-9);
    }

    @Test
    void twoUnseenBunkersPriceTheirShareOfTheGarrisonStrength() {
        BunkerPricing.Candidate first = unmeasured(BUNKER, 1.0);
        BunkerPricing.Candidate second = unmeasured(SECOND_BUNKER, 1.0);
        double fullAntiAir = HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Terran_Bunker, ALL_SMALL);

        BunkerPricing.allocate(Arrays.asList(first, second), KNOWN_MARINES);

        assertEquals(fullAntiAir, first.antiAir() + second.antiAir(), 1e-9);
    }

    @Test
    void enoughKnownInfantryLeavesEachBunkerAtItsOwnGarrison() {
        List<BunkerPricing.Candidate> bunkers = Arrays.asList(unmeasured(BUNKER, 1.0), unmeasured(SECOND_BUNKER, 1.0));

        BunkerPricing.allocate(bunkers, 2 * FULL);

        assertEquals(FULL, bunkers.get(0).getOccupants(), 1e-9);
        assertEquals(FULL, bunkers.get(1).getOccupants(), 1e-9);
    }

    @Test
    void aMeasuredGarrisonKeepsItsOccupantsAndTheOthersShareWhatIsLeft() {
        BunkerPricing.Candidate measured = new BunkerPricing.Candidate(BUNKER, false, 1.0, 3, true, 1, 1);
        BunkerPricing.Candidate unseen = unmeasured(SECOND_BUNKER, 1.0);

        BunkerPricing.allocate(Arrays.asList(measured, unseen), KNOWN_MARINES);

        assertEquals(3, measured.getOccupants(), 1e-9);
        assertEquals(1, unseen.getOccupants(), 1e-9);
    }

    @Test
    void aBunkerSeenFiringIsPricedEvenWithNoInfantryKnown() {
        BunkerPricing.Candidate measured = new BunkerPricing.Candidate(BUNKER, false, 1.0, 2, true, 1, 1);
        BunkerPricing.Candidate unseen = unmeasured(SECOND_BUNKER, 1.0);

        BunkerPricing.allocate(Arrays.asList(measured, unseen), 0);

        assertEquals(2, measured.getOccupants(), 1e-9);
        assertEquals(0, unseen.getOccupants(), 1e-9);
    }

    @Test
    void infantryAlreadyPricedWhereItWasLastSeenLeavesThePool() {
        assertEquals(1, BunkerPricing.garrisonPool(marines(KNOWN_MARINES), 3));
        assertEquals(0, BunkerPricing.garrisonPool(marines(1), 3));
    }

    @Test
    void onlyInfantryThatFiresFromABunkerFillsThePool() {
        List<ObservedUnit> living = new ArrayList<>(marines(1));
        living.add(ObservedUnitFixture.observedUnit(UnitType.Terran_Firebat, new Time(0)));
        living.add(ObservedUnitFixture.observedUnit(UnitType.Terran_Medic, new Time(0)));
        living.add(ObservedUnitFixture.observedUnit(UnitType.Terran_Goliath, new Time(0)));

        assertEquals(2, BunkerPricing.garrisonPool(living, 0));
    }

    private static ObservedUnit observedBunker(int loadedCount, int checkFrame) {
        ObservedUnit bunker = ObservedUnitFixture.observedUnit(UnitType.Terran_Bunker, new Time(0));
        bunker.setLastKnownLoadedCount(loadedCount);
        bunker.setLastLoadedCheckFrame(checkFrame);
        return bunker;
    }

    @Test
    void aSampledBunkerOutOfReachIsNotACandidate() {
        assertNull(HorizonCombatSimulator.bunkerCandidate(observedBunker(-1, 1000), BUNKER, true, 0, 1000, 1, 1));
    }

    @Test
    void anUnknownGarrisonDemandsAFullBunkerUnmeasured() {
        BunkerPricing.Candidate candidate = HorizonCombatSimulator.bunkerCandidate(observedBunker(-1, 1000), BUNKER,
                false, 1.0, 1000, 1, 1);

        BunkerPricing.allocate(Collections.singletonList(candidate), FULL);

        assertEquals(FULL, candidate.getOccupants(), 1e-9);
        assertTrue(candidate.isFogOfWar());
    }

    @Test
    void anUnseenBunkerWithNoInfantryKnownAndNoShotsIsPricedEmpty() {
        BunkerPricing.Candidate candidate = HorizonCombatSimulator.bunkerCandidate(observedBunker(-1, 1000), BUNKER,
                false, 1.0, 1000, 1, 1);

        BunkerPricing.allocate(Collections.singletonList(candidate), 0);

        assertEquals(0, candidate.antiAir(), 1e-9);
    }

    @Test
    void aMeasuredGarrisonIsPricedAtItsCountWithoutThePool() {
        BunkerPricing.Candidate candidate = HorizonCombatSimulator.bunkerCandidate(observedBunker(2, 1000), BUNKER,
                true, 1.0, 1000 + TRUST_FRAMES, 1, 1);

        BunkerPricing.allocate(Collections.singletonList(candidate), 0);

        assertEquals(2, candidate.getOccupants(), 1e-9);
    }

    @Test
    void aStaleMeasurementDemandsItsDecayedGarrisonFromThePool() {
        BunkerPricing.Candidate candidate = HorizonCombatSimulator.bunkerCandidate(observedBunker(2, 1000), BUNKER,
                true, 1.0, 1000 + TRUST_FRAMES + 1, 1, 1);

        BunkerPricing.allocate(Collections.singletonList(candidate), 0);

        assertEquals(0, candidate.getOccupants(), 1e-9);
    }

    @Test
    void pricedBunkersJoinTheSampleAndTheSnapshotAtTheirShare() {
        double fullAntiAir = HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Terran_Bunker, ALL_SMALL);
        List<BunkerPricing.Candidate> bunkers = Arrays.asList(unmeasured(BUNKER, 1.0), unmeasured(SECOND_BUNKER, 1.0));
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();

        HorizonCombatSimulator.priceBunkers(bunkers, KNOWN_MARINES, sample, snapshot, true);

        assertEquals(fullAntiAir, sample.antiAirTotal(), 1e-9);
        assertEquals(2, snapshot.getEnemyUnits().size());
        assertEquals(UnitType.Terran_Bunker, snapshot.getEnemyUnits().get(0).getType());
        assertEquals(fullAntiAir / 2, snapshot.getEnemyUnits().get(0).getStrength(), 1e-9);
    }

    @Test
    void aBunkerIsNeverFlaggedAsAThreatBeyondTheRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Terran_Bunker, 400, 320));
    }

    @Test
    void aGarrisonIsMeasuredOnlyInsideTheTrustWindow() {
        ObservedUnit bunker = ObservedUnitFixture.observedUnit(UnitType.Terran_Bunker, new Time(0));
        bunker.setLastLoadedCheckFrame(1000);

        assertFalse(HorizonCombatSimulator.bunkerGarrisonMeasured(bunker, 1000));

        bunker.setLastKnownLoadedCount(2);

        assertTrue(HorizonCombatSimulator.bunkerGarrisonMeasured(bunker, 1000 + TRUST_FRAMES));
        assertFalse(HorizonCombatSimulator.bunkerGarrisonMeasured(bunker, 1000 + TRUST_FRAMES + 1));
    }

    @Test
    void aGroundSquadThatSteppedBackOutOfReachStillPricesTheBunkerOnItsMarch() {
        Position member = new Position(BUNKER.getX() + RETREATED_CENTROID_DISTANCE, BUNKER.getY());

        assertEquals(0.0, pricedWeight(BUNKER, GROUND_REACH, standingAt(member)));
        assertEquals(1.0, pricedWeight(BUNKER, GROUND_REACH,
                BunkerPricing.memberLegs(member, null, BUILDING_BEHIND_THE_BUNKER)));
    }

    @Test
    void aMarchingSquadPricesTheBunkerTheSameInsideAndOutsideItsReach() {
        Position stepped = new Position(BUNKER.getX() + RETREATED_CENTROID_DISTANCE, BUNKER.getY());
        Position inside = new Position(BUNKER.getX() + FLIPPED_BACK_CENTROID_DISTANCE, BUNKER.getY());

        assertEquals(pricedWeight(BUNKER, GROUND_REACH, BunkerPricing.memberLegs(inside, null,
                BUILDING_BEHIND_THE_BUNKER)), pricedWeight(BUNKER, GROUND_REACH,
                BunkerPricing.memberLegs(stepped, null, BUILDING_BEHIND_THE_BUNKER)));
    }

    @Test
    void aMarchThatLeadsAwayFromTheBunkerAddsNothing() {
        Position member = new Position(BUNKER.getX() + RETREATED_CENTROID_DISTANCE, BUNKER.getY());
        Position away = new Position(member.getX() + 600, member.getY());

        assertEquals(0.0, pricedWeight(BUNKER, GROUND_REACH, BunkerPricing.memberLegs(member, null, away)));
    }

    @Test
    void theMarchLegStopsAtTheLookahead() {
        Position member = rightOfBunker(BUNKER,
                GROUND_REACH + BunkerPricing.APPROACH_FALLOFF + (int) BunkerPricing.MARCH_LOOKAHEAD + 1);
        BunkerPricing.Leg leg = BunkerPricing.marchLeg(member, BUILDING_BEHIND_THE_BUNKER);

        assertEquals(BunkerPricing.MARCH_LOOKAHEAD, member.getDistance(leg.nearestPointTo(BUNKER)), 1.0);
        assertEquals(0.0, pricedWeight(BUNKER, GROUND_REACH,
                BunkerPricing.memberLegs(member, null, BUILDING_BEHIND_THE_BUNKER)));
    }

    @Test
    void aShortMarchEndsAtItsDestination() {
        Position member = new Position(100, 100);
        Position destination = new Position(300, 100);

        assertEquals(destination, BunkerPricing.marchLeg(member, destination).nearestPointTo(new Position(900, 100)));
    }

    @Test
    void aVisibleFightTargetReplacesTheMarchLeg() {
        Position member = new Position(BUNKER.getX() + RETREATED_CENTROID_DISTANCE, BUNKER.getY());
        Position target = new Position(member.getX(), member.getY() + 100);

        List<BunkerPricing.Leg> legs = BunkerPricing.memberLegs(member, target, BUILDING_BEHIND_THE_BUNKER);

        assertEquals(2, legs.size());
        assertEquals(0.0, pricedWeight(BUNKER, GROUND_REACH, legs));
    }

    @Test
    void aMemberWithNoFightTargetOrDestinationIsItsPositionAlone() {
        Position member = new Position(100, 100);

        assertEquals(1, BunkerPricing.memberLegs(member, null, null).size());
    }

    @Test
    void aMemberMarchesToItsOwnMovementTargetBeforeTheSquadsDestination() {
        TilePosition own = new TilePosition(10, 20);

        assertEquals(own.toPosition(), BunkerPricing.marchDestination(own, BUILDING_BEHIND_THE_BUNKER));
        assertEquals(BUILDING_BEHIND_THE_BUNKER, BunkerPricing.marchDestination(null, BUILDING_BEHIND_THE_BUNKER));
        assertNull(BunkerPricing.marchDestination(null, null));
    }

    @Test
    void aMarchingSquadAtTheEdgeOfTheSampleRadiusIsPricedWithoutAStep() {
        double edge = HorizonCombatSimulator.edgeOfFireRadius(GROUND_REACH);
        Position inside = new Position(BUNKER.getX() + (int) edge - 1, BUNKER.getY());
        Position outside = new Position(BUNKER.getX() + (int) edge + 1, BUNKER.getY());
        List<BunkerPricing.Leg> insideLegs = BunkerPricing.memberLegs(inside, null, BUILDING_BEHIND_THE_BUNKER);
        List<BunkerPricing.Leg> outsideLegs = BunkerPricing.memberLegs(outside, null, BUILDING_BEHIND_THE_BUNKER);

        assertEquals(1.0, pricedWeight(BUNKER, GROUND_REACH, insideLegs));
        assertEquals(1.0, pricedWeight(BUNKER, GROUND_REACH, outsideLegs));
        assertTrue(inside.getDistance(BUNKER) <= edge);
        assertTrue(outside.getDistance(BUNKER) > edge);
        double insideWeight = BunkerPricing.weight(BUNKER, inside, insideLegs, GROUND_REACH);
        assertEquals(1.0 / BunkerPricing.RADIUS_TAPER, insideWeight, 1e-9);
        assertEquals(0.0, BunkerPricing.weight(BUNKER, outside, outsideLegs, GROUND_REACH));
    }

    @Test
    void theRadiusTaperFallsLinearlyOverTheOuterBandOfTheSampleRadius() {
        double edge = HorizonCombatSimulator.edgeOfFireRadius(GROUND_REACH);

        assertEquals(1.0, BunkerPricing.radiusTaper(0, GROUND_REACH));
        assertEquals(1.0, BunkerPricing.radiusTaper(edge - BunkerPricing.RADIUS_TAPER, GROUND_REACH));
        assertEquals(0.5, BunkerPricing.radiusTaper(edge - BunkerPricing.RADIUS_TAPER / 2, GROUND_REACH), 1e-9);
        assertEquals(0.0, BunkerPricing.radiusTaper(edge, GROUND_REACH));
        assertEquals(0.0, BunkerPricing.radiusTaper(edge + 1, GROUND_REACH));
    }

    @Test
    void aFarBunkerLeavingTheSampleRadiusDoesNotStepTheNearBunkersGarrison() {
        double edge = HorizonCombatSimulator.edgeOfFireRadius(GROUND_REACH);
        Position center = rightOfBunker(BUNKER, GROUND_REACH);
        Position justInside = new Position(center.getX() + (int) edge - 1, center.getY());
        Position justOutside = new Position(center.getX() + (int) edge + 1, center.getY());
        List<BunkerPricing.Leg> legs = standingAt(center);
        BunkerPricing.Candidate nearWithFar = unmeasured(BUNKER, BunkerPricing.weight(BUNKER, center, legs,
                GROUND_REACH));
        BunkerPricing.Candidate far = unmeasured(justInside, BunkerPricing.weight(justInside, center,
                Collections.singletonList(new BunkerPricing.Leg(center, justInside)), GROUND_REACH));
        BunkerPricing.Candidate nearAlone = unmeasured(BUNKER, BunkerPricing.weight(BUNKER, center, legs,
                GROUND_REACH));

        BunkerPricing.allocate(Arrays.asList(nearWithFar, far), KNOWN_MARINES);
        BunkerPricing.allocate(Collections.singletonList(nearAlone), KNOWN_MARINES);

        assertEquals(0.0, BunkerPricing.weight(justOutside, center,
                Collections.singletonList(new BunkerPricing.Leg(center, justOutside)), GROUND_REACH));
        assertTrue(far.ground() + nearWithFar.ground() <= nearAlone.ground() * 1.01);
        assertEquals(nearAlone.getOccupants(), nearWithFar.getOccupants(), 0.05);
    }

    @Test
    void aSquadWellInsideTheSampleRadiusIsPricedAtItsFireWeight() {
        Position member = rightOfBunker(BUNKER, GROUND_REACH);
        List<BunkerPricing.Leg> legs = standingAt(member);

        assertEquals(pricedWeight(BUNKER, GROUND_REACH, legs), BunkerPricing.weight(BUNKER, member, legs, GROUND_REACH));
        assertEquals(1.0, BunkerPricing.weight(BUNKER, member, legs, GROUND_REACH));
    }

    @Test
    void theSquadMarchesToTheClosestKnownEnemyBuilding() {
        Position center = new Position(2000, 1000);
        Position far = new Position(100, 100);

        assertEquals(BUILDING_BEHIND_THE_BUNKER,
                BunkerPricing.squadDestination(center, Arrays.asList(far, BUILDING_BEHIND_THE_BUNKER)));
        assertNull(BunkerPricing.squadDestination(center, Collections.emptyList()));
    }
}
