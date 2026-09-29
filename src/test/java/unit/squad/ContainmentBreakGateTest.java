package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.BunkerGarrison;
import org.junit.jupiter.api.Test;
import unit.squad.ContainmentEvaluator.ArmySighting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.ContainmentEvaluator.ARMY_MEMORY_FRAMES;
import static unit.squad.ContainmentEvaluator.NEAR_CONTAIN_RADIUS;
import static unit.squad.ContainmentEvaluator.PARKED_SUPPLY_PER_BUNKER;
import static unit.squad.ContainmentEvaluator.STATIC_COVER_RADIUS;
import static unit.squad.ContainmentEvaluator.STATIC_DEFENSE_BASE_RADIUS;
import static unit.squad.ContainmentEvaluator.breaks;
import static unit.squad.ContainmentEvaluator.bunkerSupply;
import static unit.squad.ContainmentEvaluator.countsTowardBreak;
import static unit.squad.ContainmentEvaluator.defendsBase;
import static unit.squad.ContainmentEvaluator.isArmyUnit;
import static unit.squad.ContainmentEvaluator.isFreshSighting;
import static unit.squad.ContainmentEvaluator.staticOnly;

class ContainmentBreakGateTest {

    private static final int UNKNOWN = -1;
    private static final Position ENEMY_MAIN = new Position(240, 400);
    private static final Position CONTAIN = new Position(240, 1100);
    private static final List<Position> CONTESTED = Arrays.asList(ENEMY_MAIN, CONTAIN);
    private static final Position OVER_THE_MAIN = new Position(540, 400);
    private static final Position AT_OUR_RALLY = new Position(3000, 3000);
    private static final Position BUNKER_WEST = new Position(176, 672);
    private static final Position BUNKER_EAST = new Position(272, 736);
    private static final Position CANNON_NORTH = new Position(3024, 400);
    private static final Position CANNON_SOUTH = new Position(3024, 496);

    @Test
    void anAirSquadRetreatingOrRallyingNearTheContainCountsTowardTheBreak() {
        assertTrue(countsTowardBreak(SquadStatus.HARASS, OVER_THE_MAIN, CONTESTED));
        assertTrue(countsTowardBreak(SquadStatus.RETREAT, OVER_THE_MAIN, CONTESTED));
        assertTrue(countsTowardBreak(SquadStatus.RALLY, OVER_THE_MAIN, CONTESTED));
        assertTrue(countsTowardBreak(SquadStatus.RETREAT, new Position(240, 1100 + NEAR_CONTAIN_RADIUS), CONTESTED));
    }

    @Test
    void aSquadAwayFromEveryContestedPositionCountsOnlyWhileFightingOrContaining() {
        assertFalse(countsTowardBreak(SquadStatus.RETREAT, AT_OUR_RALLY, CONTESTED));
        assertFalse(countsTowardBreak(SquadStatus.RALLY, AT_OUR_RALLY, CONTESTED));
        assertFalse(countsTowardBreak(SquadStatus.RETREAT, new Position(240, 1101 + NEAR_CONTAIN_RADIUS), CONTESTED));
        assertTrue(countsTowardBreak(SquadStatus.FIGHT, AT_OUR_RALLY, CONTESTED));
        assertTrue(countsTowardBreak(SquadStatus.CONTAIN, AT_OUR_RALLY, CONTESTED));
    }

    @Test
    void aDefenceOrRunbySquadOrOneWithNoStatusNeverCounts() {
        assertFalse(countsTowardBreak(SquadStatus.DEFENSE, OVER_THE_MAIN, CONTESTED));
        assertFalse(countsTowardBreak(SquadStatus.RUNBY, OVER_THE_MAIN, CONTESTED));
        assertFalse(countsTowardBreak(null, OVER_THE_MAIN, CONTESTED));
    }

    @Test
    void bunkersArePricedByTheGarrisonSeenNotAFixedPenaltyEach() {
        int marine = UnitType.Terran_Marine.supplyRequired();
        assertEquals(4 * marine, bunkerSupply(Arrays.asList(UNKNOWN, UNKNOWN), 4));
        assertEquals(0, bunkerSupply(Arrays.asList(0, 0), 4));
        assertEquals(marine, bunkerSupply(Arrays.asList(1, UNKNOWN), 1));
        assertEquals(8 * marine, bunkerSupply(Arrays.asList(4, 4), 8));
    }

    @Test
    void lyrgh0iuBreaksOnceTheMutasOverTheMainCountAndTheBunkersArePricedByTheirMarines() {
        int enemyArmy = 4 * UnitType.Terran_Marine.supplyRequired() + 3 * UnitType.Terran_Goliath.supplyRequired();
        int enemy = enemyArmy + bunkerSupply(Arrays.asList(UNKNOWN, UNKNOWN), 4);
        int lings = 36 * UnitType.Zerg_Zergling.supplyRequired();
        int mutas = 10 * UnitType.Zerg_Mutalisk.supplyRequired();
        assertFalse(breaks(lings, enemy));
        assertTrue(breaks(lings + mutas, enemy));
    }

    private static ArmySighting seen(Position position, UnitType type) {
        return new ArmySighting(position, type.supplyRequired(), true, BunkerGarrison.OCCUPANTS.contains(type));
    }

    private static ArmySighting remembered(Position position, UnitType type) {
        return new ArmySighting(position, type.supplyRequired(), false, BunkerGarrison.OCCUPANTS.contains(type));
    }

    private static List<ArmySighting> copies(int count, ArmySighting sighting) {
        return new ArrayList<>(Collections.nCopies(count, sighting));
    }

    @Test
    void anArmyWalledInBehindItsBunkersIsStaticOnly() {
        List<Position> bunkers = Arrays.asList(BUNKER_WEST, BUNKER_EAST);
        List<ArmySighting> goliaths = Arrays.asList(seen(new Position(178, 779), UnitType.Terran_Goliath),
                seen(new Position(233, 777), UnitType.Terran_Goliath),
                seen(new Position(186, 631), UnitType.Terran_Goliath));
        assertTrue(staticOnly(bunkers, bunkers, goliaths));
        assertTrue(staticOnly(bunkers, bunkers, Collections.emptyList()));
    }

    @Test
    void anArmyUnitOutsideTheStaticDefenceOrWithNoKnownPositionIsNotStaticOnly() {
        List<Position> bunkers = Collections.singletonList(BUNKER_WEST);
        Position justOutside = new Position(BUNKER_WEST.getX() + STATIC_COVER_RADIUS + 1, BUNKER_WEST.getY());
        Position justInside = new Position(BUNKER_WEST.getX() + STATIC_COVER_RADIUS, BUNKER_WEST.getY());
        assertTrue(staticOnly(bunkers, bunkers,
                Collections.singletonList(seen(justInside, UnitType.Terran_Vulture))));
        assertFalse(staticOnly(bunkers, bunkers,
                Collections.singletonList(seen(justOutside, UnitType.Terran_Vulture))));
        assertFalse(staticOnly(bunkers, bunkers, Collections.singletonList(seen(null, UnitType.Terran_Vulture))));
    }

    @Test
    void noStaticDefenceIsNeverStaticOnly() {
        assertFalse(staticOnly(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    void m0jdw0p0ZealotsRememberedUnderTheCannonsFromMinutesAgoAreUnknownNotStatic() {
        List<Position> cannons = Arrays.asList(CANNON_NORTH, CANNON_SOUTH);
        List<ArmySighting> zealots = Arrays.asList(remembered(new Position(3050, 420), UnitType.Protoss_Zealot),
                remembered(new Position(3040, 530), UnitType.Protoss_Zealot));
        assertFalse(staticOnly(cannons, Collections.emptyList(), zealots));
        assertTrue(staticOnly(cannons, Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    void anArmySightingIsFreshWhileVisibleOrForTheMemoryWindowAfterItWasLastSeen() {
        assertTrue(isFreshSighting(true, 0, 20000));
        assertTrue(isFreshSighting(false, 9000, 9000 + ARMY_MEMORY_FRAMES));
        assertFalse(isFreshSighting(false, 9000, 9001 + ARMY_MEMORY_FRAMES));
    }

    @Test
    void noArmyMayStandUnderADefenceWithNoBunker() {
        List<Position> cannons = Collections.singletonList(CANNON_NORTH);
        assertFalse(staticOnly(cannons, Collections.emptyList(),
                Collections.singletonList(seen(new Position(3050, 420), UnitType.Protoss_Zealot))));
    }

    @Test
    void lyrgh0iuLoadedMarinesLongUnseenAreTheGarrisonAndTheGoliathsFitTheParkedAllowance() {
        List<Position> bunkers = Arrays.asList(BUNKER_WEST, BUNKER_EAST);
        List<ArmySighting> army = copies(4, remembered(new Position(190, 690), UnitType.Terran_Marine));
        army.add(seen(new Position(178, 779), UnitType.Terran_Goliath));
        army.add(seen(new Position(233, 777), UnitType.Terran_Goliath));
        army.add(seen(new Position(186, 631), UnitType.Terran_Goliath));
        assertTrue(staticOnly(bunkers, bunkers, army));
    }

    @Test
    void occupantsBeyondTheBunkersSlotsAreParkedArmyAndMustBeFresh() {
        List<Position> bunkers = Collections.singletonList(BUNKER_WEST);
        Position atTheBunker = new Position(BUNKER_WEST.getX() + 32, BUNKER_WEST.getY());
        assertTrue(staticOnly(bunkers, bunkers, copies(BunkerGarrison.MAX_GARRISON,
                remembered(atTheBunker, UnitType.Terran_Marine))));
        assertFalse(staticOnly(bunkers, bunkers, copies(BunkerGarrison.MAX_GARRISON + 1,
                remembered(atTheBunker, UnitType.Terran_Marine))));
    }

    @Test
    void aRememberedOccupantAwayFromEveryBunkerIsUnknown() {
        List<Position> defences = Arrays.asList(BUNKER_WEST, CANNON_NORTH);
        List<Position> bunkers = Collections.singletonList(BUNKER_WEST);
        assertFalse(staticOnly(defences, bunkers, Collections.singletonList(
                remembered(new Position(3050, 420), UnitType.Terran_Marine))));
    }

    @Test
    void m0jdw0h6AMechArmyParkedUnderTheBunkersIsNotStaticOnly() {
        List<Position> bunkers = Arrays.asList(BUNKER_WEST, BUNKER_EAST);
        Position behind = new Position(224, 760);
        List<ArmySighting> mech = copies(6, seen(behind, UnitType.Terran_Siege_Tank_Siege_Mode));
        mech.addAll(copies(6, seen(behind, UnitType.Terran_Goliath)));
        mech.addAll(copies(6, seen(behind, UnitType.Terran_Vulture)));
        assertFalse(staticOnly(bunkers, bunkers, mech));
    }

    @Test
    void theParkedArmyMayReachAGarrisonEquivalentPerBunkerAndNoMore() {
        List<Position> bunkers = Arrays.asList(BUNKER_WEST, BUNKER_EAST);
        Position behind = new Position(224, 760);
        int goliath = UnitType.Terran_Goliath.supplyRequired();
        int atAllowance = 2 * PARKED_SUPPLY_PER_BUNKER / goliath;
        assertEquals(2 * PARKED_SUPPLY_PER_BUNKER, atAllowance * goliath);
        assertTrue(staticOnly(bunkers, bunkers, copies(atAllowance, seen(behind, UnitType.Terran_Goliath))));
        List<ArmySighting> over = copies(atAllowance, seen(behind, UnitType.Terran_Goliath));
        over.add(seen(behind, UnitType.Terran_Vulture));
        assertFalse(staticOnly(bunkers, bunkers, over));
    }

    @Test
    void onlyADefenceAtTheEnemyMainOrNaturalShelters() {
        List<Position> bases = Collections.singletonList(ENEMY_MAIN);
        assertTrue(defendsBase(new Position(ENEMY_MAIN.getX() + STATIC_DEFENSE_BASE_RADIUS, ENEMY_MAIN.getY()),
                bases));
        assertFalse(defendsBase(new Position(ENEMY_MAIN.getX() + STATIC_DEFENSE_BASE_RADIUS + 1, ENEMY_MAIN.getY()),
                bases));
        assertFalse(defendsBase(ENEMY_MAIN, Collections.emptyList()));
    }

    @Test
    void workersBuildingsOverlordsAndEggsAreNotArmy() {
        assertTrue(isArmyUnit(UnitType.Terran_Goliath));
        assertTrue(isArmyUnit(UnitType.Terran_Marine));
        assertTrue(isArmyUnit(UnitType.Protoss_Dragoon));
        assertFalse(isArmyUnit(UnitType.Terran_SCV));
        assertFalse(isArmyUnit(UnitType.Terran_Bunker));
        assertFalse(isArmyUnit(UnitType.Zerg_Overlord));
        assertFalse(isArmyUnit(UnitType.Zerg_Egg));
        assertFalse(isArmyUnit(UnitType.Zerg_Larva));
    }
}
