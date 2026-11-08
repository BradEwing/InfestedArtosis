package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeMinRangeTest {

    private static final UnitType SIEGED = UnitType.Terran_Siege_Tank_Siege_Mode;
    private static final UnitType ZERGLING = UnitType.Zerg_Zergling;
    private static final Position TANK = new Position(1000, 1000);
    private static final int REACH = SIEGED.groundWeapon().maxRange();
    private static final double EPSILON = 1e-9;
    private static final int[][] SURROUND = {{40, 0}, {-40, 0}, {0, 36}, {0, -36}, {28, 28}, {-28, 28}, {28, -28},
        {-28, -28}};

    private final boolean setting = Config.siegeMinRange;

    @AfterEach
    void restore() {
        Config.siegeMinRange = setting;
    }

    private static SiegeMinRange.Member ling(int dx, int dy) {
        return new SiegeMinRange.Member(ZERGLING, new Position(TANK.getX() + dx, TANK.getY() + dy), 1.0);
    }

    private static GroundBattle surrounded() {
        GroundBattle battle = new GroundBattle();
        for (int[] offset : SURROUND) {
            battle.zergling(offset[0], offset[1]);
        }
        return battle;
    }

    private static double tankStrength(HorizonCombatSimulator.DebugSnapshot snapshot, int dx, int dy) {
        Position at = new Position(GroundBattle.CENTRE.getX() + dx, GroundBattle.CENTRE.getY() + dy);
        return snapshot.getEnemyUnits().stream()
                .filter(e -> e.getType() == SIEGED && e.getPosition().equals(at))
                .mapToDouble(HorizonCombatSimulator.UnitDebugEntry::getStrength)
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    @Test
    void theMinimumRangeIsReadFromTheSiegedTanksWeapon() {
        assertEquals(SIEGED.groundWeapon().minRange(), SiegeMinRange.MIN_RANGE);
        assertTrue(SiegeMinRange.MIN_RANGE > 0);
        assertEquals(SiegeMinRange.MIN_RANGE, HorizonCombatSimulator.siegeMinRange());
    }

    @Test
    void zerglingsWrappedAroundATankAreAllInsideItsMinimumRange() {
        List<SiegeMinRange.Member> members = Arrays.asList(ling(40, 0), ling(-40, 0), ling(0, 36), ling(28, -28));

        assertEquals(0.0, SiegeMinRange.hitShare(members, TANK), EPSILON);
    }

    @Test
    void aSecondTankCoveringTheSameZerglingsFiresOnAllOfThem() {
        List<SiegeMinRange.Member> members = Arrays.asList(ling(40, 0), ling(-40, 0), ling(0, 36), ling(28, -28));
        Position second = new Position(TANK.getX() + 200, TANK.getY());

        assertEquals(1.0, SiegeMinRange.hitShare(members, second), EPSILON);
        SiegeMinRange.Shares shares = SiegeMinRange.shares(members, Arrays.asList(
                new SiegeMinRange.Tank(TANK, REACH, 0), new SiegeMinRange.Tank(second, REACH, 1)));
        assertEquals(1.0, shares.getInside(), EPSILON);
        assertEquals(1.0, shares.getCovered(), EPSILON);
    }

    @Test
    void theHitShareIsTheStrengthOutsideTheMinimumRange() {
        List<SiegeMinRange.Member> members = Arrays.asList(ling(30, 0),
                new SiegeMinRange.Member(ZERGLING, new Position(TANK.getX() + 250, TANK.getY()), 3.0));

        assertEquals(0.75, SiegeMinRange.hitShare(members, TANK), EPSILON);
    }

    @Test
    void aMemberBetweenTheMinimumRangeAndTheReachIsCovered() {
        SiegeMinRange.Tank tank = new SiegeMinRange.Tank(TANK, REACH, 1);

        assertTrue(SiegeMinRange.covered(ling(200, 0), tank));
        assertTrue(!SiegeMinRange.covered(ling(30, 0), tank));
        assertTrue(!SiegeMinRange.covered(ling(REACH + 100, 0), tank));
    }

    @Test
    void noGroundStrengthLeavesTheTankAtFull() {
        assertEquals(1.0, SiegeMinRange.hitShare(Collections.emptyList(), TANK), EPSILON);
        SiegeMinRange.Shares shares = SiegeMinRange.shares(Collections.emptyList(),
                Collections.singletonList(new SiegeMinRange.Tank(TANK, REACH, 1)));
        assertEquals(0.0, shares.getInside(), EPSILON);
        assertEquals(0.0, shares.getCovered(), EPSILON);
    }

    @Test
    void aTankWithEveryZerglingInsideItsMinimumRangePricesAtNothing() {
        Config.siegeMinRange = true;
        GroundBattle battle = surrounded().enemy(SIEGED, 0, 0);
        battle.evaluate();

        HorizonCombatSimulator.DebugSnapshot snapshot = battle.snapshot();
        assertEquals(0.0, tankStrength(snapshot, 0, 0), EPSILON);
        assertEquals(0.0, snapshot.getSiegeStrengthPriced(), EPSILON);
        assertTrue(snapshot.getSiegeStrengthMain() > 0);
        assertEquals(1.0, snapshot.getSiegeMinRangeShare(), EPSILON);
        assertEquals(0.0, snapshot.getSiegeCoveredShare(), EPSILON);
    }

    @Test
    void aSecondTankCoveringTheSurroundPricesAtFull() {
        Config.siegeMinRange = false;
        GroundBattle off = surrounded().enemy(SIEGED, 0, 0).enemy(SIEGED, 200, 0);
        off.evaluate();
        double full = tankStrength(off.snapshot(), 200, 0);

        Config.siegeMinRange = true;
        GroundBattle on = surrounded().enemy(SIEGED, 0, 0).enemy(SIEGED, 200, 0);
        on.evaluate();

        assertTrue(full > 0);
        assertEquals(full, tankStrength(on.snapshot(), 200, 0), EPSILON);
        assertEquals(0.0, tankStrength(on.snapshot(), 0, 0), EPSILON);
        assertEquals(full, on.snapshot().getSiegeStrengthPriced(), EPSILON);
        assertEquals(1.0, on.snapshot().getSiegeMinRangeShare(), EPSILON);
        assertEquals(1.0, on.snapshot().getSiegeCoveredShare(), EPSILON);
        assertEquals(0.0, on.snapshot().getSiegeHitShares().get(new Position(GroundBattle.CENTRE.getX(),
                GroundBattle.CENTRE.getY())), EPSILON);
    }

    @Test
    void aSquadBetweenTheMinimumRangeAndTheReachIsPricedAsWithTheSwitchOff() {
        Config.siegeMinRange = false;
        GroundBattle off = surrounded().enemy(SIEGED, 250, 0);
        off.evaluate();
        Config.siegeMinRange = true;
        GroundBattle on = surrounded().enemy(SIEGED, 250, 0);
        on.evaluate();

        assertEquals(off.snapshot().getEnemyTotal(), on.snapshot().getEnemyTotal(), EPSILON);
        assertEquals(off.snapshot().getOverallRatio(), on.snapshot().getOverallRatio(), EPSILON);
        assertEquals(off.snapshot().getResult(), on.snapshot().getResult());
        assertEquals(on.snapshot().getSiegeStrengthMain(), on.snapshot().getSiegeStrengthPriced(), EPSILON);
        assertEquals(0.0, on.snapshot().getSiegeMinRangeShare(), EPSILON);
        assertEquals(1.0, on.snapshot().getSiegeCoveredShare(), EPSILON);
    }

    @Test
    void withTheSwitchOffATankAtTheZerglingsFeetPricesAtFull() {
        Config.siegeMinRange = false;
        GroundBattle battle = surrounded().enemy(SIEGED, 0, 0);
        battle.evaluate();

        HorizonCombatSimulator.DebugSnapshot snapshot = battle.snapshot();
        assertTrue(tankStrength(snapshot, 0, 0) > 0);
        assertEquals(snapshot.getSiegeStrengthMain(), snapshot.getSiegeStrengthPriced(), EPSILON);
        assertEquals(snapshot.getSiegeStrengthMain(), tankStrength(snapshot, 0, 0), EPSILON);
        assertEquals(snapshot.getSiegeStrengthMain(), snapshot.getEnemyTotal(), EPSILON);
        assertEquals(1.0, snapshot.getSiegeMinRangeShare(), EPSILON);
    }

    @Test
    void halfTheSquadAtTheTanksFeetHalvesItsPrice() {
        Config.siegeMinRange = true;
        GroundBattle battle = new GroundBattle()
                .zergling(30, 0).zergling(-30, 0).zergling(0, 30).zergling(0, -30)
                .zergling(300, 0).zergling(300, 30).zergling(300, -30).zergling(330, 0)
                .enemy(SIEGED, 0, 0);
        battle.evaluate();

        HorizonCombatSimulator.DebugSnapshot snapshot = battle.snapshot();
        assertEquals(0.5, snapshot.getSiegeMinRangeShare(), EPSILON);
        assertEquals(0.5, snapshot.getSiegeCoveredShare(), EPSILON);
        assertEquals(0.5 * snapshot.getSiegeStrengthMain(), snapshot.getSiegeStrengthPriced(), EPSILON);
    }

    @Test
    void aReadWithNoSiegedTankLeavesTheSharesUnset() {
        GroundBattle battle = surrounded().enemy(UnitType.Terran_Marine, 100, 0);
        battle.evaluate();

        HorizonCombatSimulator.DebugSnapshot snapshot = battle.snapshot();
        assertEquals(-1, snapshot.getSiegeMinRangeShare(), EPSILON);
        assertEquals(-1, snapshot.getSiegeCoveredShare(), EPSILON);
        assertEquals(-1, snapshot.getSiegeStrengthMain(), EPSILON);
        assertEquals(-1, snapshot.getSiegeStrengthPriced(), EPSILON);
        assertTrue(snapshot.getSiegeHitShares().isEmpty());
    }

    @Test
    void anAirSquadOverATankIsPricedTheSameEitherWay() {
        Config.siegeMinRange = false;
        AirBattle off = new AirBattle(6).enemy(SIEGED, 0, 0).enemy(UnitType.Terran_Goliath, 100, 0);
        off.evaluate();
        Config.siegeMinRange = true;
        AirBattle on = new AirBattle(6).enemy(SIEGED, 0, 0).enemy(UnitType.Terran_Goliath, 100, 0);
        on.evaluate();

        assertEquals(off.snapshot().getEnemyTotal(), on.snapshot().getEnemyTotal(), EPSILON);
        assertEquals(off.snapshot().getOverallRatio(), on.snapshot().getOverallRatio(), EPSILON);
        assertEquals(-1, on.snapshot().getSiegeMinRangeShare(), EPSILON);
        assertTrue(on.snapshot().getSiegeHitShares().isEmpty());
    }
}
