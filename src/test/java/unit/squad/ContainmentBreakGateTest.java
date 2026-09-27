package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.ContainmentEvaluator.NEAR_CONTAIN_RADIUS;
import static unit.squad.ContainmentEvaluator.STATIC_COVER_RADIUS;
import static unit.squad.ContainmentEvaluator.breaks;
import static unit.squad.ContainmentEvaluator.bunkerSupply;
import static unit.squad.ContainmentEvaluator.countsTowardBreak;
import static unit.squad.ContainmentEvaluator.isArmyUnit;
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

    @Test
    void anAirSquadRetreatingOrRallyingNearTheContainCountsTowardTheBreak() {
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

    @Test
    void anArmyWalledInBehindItsBunkersIsStaticOnly() {
        List<Position> bunkers = Arrays.asList(BUNKER_WEST, BUNKER_EAST);
        List<Position> goliaths = Arrays.asList(new Position(178, 779), new Position(233, 777),
                new Position(186, 631));
        assertTrue(staticOnly(bunkers, goliaths));
        assertTrue(staticOnly(bunkers, Collections.emptyList()));
    }

    @Test
    void anArmyUnitOutsideTheStaticDefenceOrWithNoKnownPositionIsNotStaticOnly() {
        List<Position> bunkers = Collections.singletonList(BUNKER_WEST);
        Position justOutside = new Position(BUNKER_WEST.getX() + STATIC_COVER_RADIUS + 1, BUNKER_WEST.getY());
        Position justInside = new Position(BUNKER_WEST.getX() + STATIC_COVER_RADIUS, BUNKER_WEST.getY());
        assertTrue(staticOnly(bunkers, Collections.singletonList(justInside)));
        assertFalse(staticOnly(bunkers, Collections.singletonList(justOutside)));
        assertFalse(staticOnly(bunkers, Collections.singletonList(null)));
    }

    @Test
    void noStaticDefenceIsNeverStaticOnly() {
        assertFalse(staticOnly(Collections.emptyList(), Collections.emptyList()));
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
