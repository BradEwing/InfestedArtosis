package unit.squad.horizon;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import info.tracking.DarkSwarm;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class SwarmCoverTest {

    private static final double TOLERANCE = 1e-9;
    private static final Position CENTRE = new Position(1232, 3520);
    private static final double LING_SPEED = 6.0;
    private static final Map<UnitSizeType, Double> ALL_SMALL = Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final double TERRAN_ENGAGE_THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Terran);

    private static List<DarkSwarm> swarmWith(int remainingFrames) {
        return Collections.singletonList(new DarkSwarm(382, CENTRE, remainingFrames));
    }

    @Test
    void ordinaryRangedDirectAttacksAreNegated() {
        assertTrue(SwarmCover.isNegated(UnitType.Terran_Marine));
        assertTrue(SwarmCover.isNegated(UnitType.Terran_Goliath));
        assertTrue(SwarmCover.isNegated(UnitType.Terran_Vulture));
        assertTrue(SwarmCover.isNegated(UnitType.Terran_Ghost));
        assertTrue(SwarmCover.isNegated(UnitType.Terran_Siege_Tank_Tank_Mode));
        assertTrue(SwarmCover.isNegated(UnitType.Terran_Bunker));
        assertTrue(SwarmCover.isNegated(UnitType.Protoss_Dragoon));
        assertTrue(SwarmCover.isNegated(UnitType.Protoss_Photon_Cannon));
        assertTrue(SwarmCover.isNegated(UnitType.Zerg_Hydralisk));
    }

    @Test
    void siegedTankSplashMeleeAndOtherSplashStayPriced() {
        assertFalse(SwarmCover.isNegated(UnitType.Terran_Siege_Tank_Siege_Mode));
        assertFalse(SwarmCover.isNegated(UnitType.Terran_Firebat));
        assertFalse(SwarmCover.isNegated(UnitType.Terran_Vulture_Spider_Mine));
        assertFalse(SwarmCover.isNegated(UnitType.Terran_SCV));
        assertFalse(SwarmCover.isNegated(UnitType.Protoss_Zealot));
        assertFalse(SwarmCover.isNegated(UnitType.Protoss_Archon));
        assertFalse(SwarmCover.isNegated(UnitType.Protoss_Reaver));
        assertFalse(SwarmCover.isNegated(UnitType.Zerg_Zergling));
        assertFalse(SwarmCover.isNegated(UnitType.Zerg_Ultralisk));
        assertFalse(SwarmCover.isNegated(UnitType.Zerg_Lurker));
        assertFalse(SwarmCover.isNegated(UnitType.Zerg_Sunken_Colony));
    }

    @Test
    void fullCoverRemovesTheNegatedStrengthUpToTheResidualAndLeavesTheRestWhole() {
        assertEquals(1 - SwarmCover.MAX_NEGATION, SwarmCover.groundMultiplier(UnitType.Terran_Goliath, 1.0),
                TOLERANCE);
        assertEquals(1.0, SwarmCover.groundMultiplier(UnitType.Terran_Siege_Tank_Siege_Mode, 1.0), TOLERANCE);
        assertEquals(1.0, SwarmCover.groundMultiplier(UnitType.Terran_Firebat, 1.0), TOLERANCE);
        assertEquals(1.0, SwarmCover.groundMultiplier(UnitType.Terran_Goliath, 0), TOLERANCE);
        assertEquals(1 - 0.5 * SwarmCover.MAX_NEGATION, SwarmCover.groundMultiplier(UnitType.Terran_Marine, 0.5),
                TOLERANCE);
    }

    @Test
    void aUnitUnderTheSwarmWithTheHorizonLeftIsFullyCovered() {
        assertEquals(1.0, SwarmCover.unitCover(CENTRE, UnitType.Zerg_Zergling, LING_SPEED,
                swarmWith(SwarmCover.HORIZON_FRAMES)), TOLERANCE);
        assertEquals(1.0, SwarmCover.unitCover(CENTRE, UnitType.Zerg_Zergling, LING_SPEED, swarmWith(900)),
                TOLERANCE);
    }

    @Test
    void coverScalesWithTheShareOfTheHorizonTheSwarmHasLeft() {
        assertEquals(0.5, SwarmCover.unitCover(CENTRE, UnitType.Zerg_Zergling, LING_SPEED,
                swarmWith(SwarmCover.HORIZON_FRAMES / 2)), TOLERANCE);
        assertEquals(0, SwarmCover.unitCover(CENTRE, UnitType.Zerg_Zergling, LING_SPEED, swarmWith(0)), TOLERANCE);
    }

    @Test
    void coverFallsWithTheGapToTheFootprintAndIsGoneAtTheNearDistance() {
        assertEquals(0.5, SwarmCover.cover(SwarmCover.NEAR_DISTANCE / 2, LING_SPEED, 900), TOLERANCE);
        assertEquals(0, SwarmCover.cover(SwarmCover.NEAR_DISTANCE, LING_SPEED, 900), TOLERANCE);
        assertTrue(SwarmCover.cover(10, LING_SPEED, 900) > SwarmCover.cover(100, LING_SPEED, 900));
    }

    @Test
    void theWalkToTheFootprintIsTakenOutOfTheSwarmsRemainingTime() {
        double gap = 60;
        double travel = gap / LING_SPEED;
        double proximity = 1 - gap / SwarmCover.NEAR_DISTANCE;

        assertEquals(proximity * (SwarmCover.HORIZON_FRAMES - travel) / SwarmCover.HORIZON_FRAMES,
                SwarmCover.cover(gap, LING_SPEED, SwarmCover.HORIZON_FRAMES), TOLERANCE);
        assertEquals(0, SwarmCover.cover(gap, LING_SPEED, (int) travel), TOLERANCE);
        assertEquals(0, SwarmCover.cover(gap, 0, 900), TOLERANCE);
    }

    @Test
    void flyersAndBuildingsGetNoCover() {
        assertEquals(0, SwarmCover.unitCover(CENTRE, UnitType.Zerg_Mutalisk, LING_SPEED, swarmWith(900)), TOLERANCE);
        assertEquals(0, SwarmCover.unitCover(CENTRE, UnitType.Zerg_Sunken_Colony, 0, swarmWith(900)), TOLERANCE);
    }

    @Test
    void aUnitTakesTheBestOfTheSwarms() {
        List<DarkSwarm> swarms = Arrays.asList(new DarkSwarm(1, CENTRE, 30), new DarkSwarm(2, CENTRE, 900));

        assertEquals(1.0, SwarmCover.unitCover(CENTRE, UnitType.Zerg_Zergling, LING_SPEED, swarms), TOLERANCE);
    }

    @Test
    void theSquadCoverIsWeightedByStrength() {
        assertEquals(0.75, SwarmCover.coverShare(Arrays.asList(1.0, 0.0), Arrays.asList(3.0, 1.0)), TOLERANCE);
        assertEquals(0, SwarmCover.coverShare(Collections.emptyList(), Collections.emptyList()), TOLERANCE);
    }

    @Test
    void goliathsAndMarinesUnderOurSwarmNoLongerTurnZerglingsAway() {
        UnitType goliath = UnitType.Terran_Goliath;
        UnitType marine = UnitType.Terran_Marine;
        UnitType ling = UnitType.Zerg_Zergling;
        UnitType[] enemies = {goliath, goliath, marine, marine};
        UnitType[] ours = {ling, ling, ling, ling};

        assertEquals(RETREAT, verdict(ours, enemies, 0));
        assertEquals(ENGAGE, verdict(ours, enemies, 1.0));
    }

    @Test
    void siegedTanksKeepTurningZerglingsAwayUnderOurSwarm() {
        UnitType[] enemies = {UnitType.Terran_Siege_Tank_Siege_Mode, UnitType.Terran_Siege_Tank_Siege_Mode};
        UnitType[] ours = {UnitType.Zerg_Zergling, UnitType.Zerg_Zergling};

        assertEquals(verdict(ours, enemies, 0), verdict(ours, enemies, 1.0));
        assertEquals(ratio(ours, enemies, 0), ratio(ours, enemies, 1.0), TOLERANCE);
    }

    @Test
    void aNegatedEnemyIsNoSmallerATarget() {
        HorizonCombatSimulator.EnemySample covered = sample(1.0, UnitType.Terran_Goliath);
        HorizonCombatSimulator.EnemySample bare = sample(0, UnitType.Terran_Goliath);

        assertEquals(bare.getGroundStanding(), covered.getGroundStanding(), TOLERANCE);
        assertTrue(covered.groundTotal() < bare.groundTotal());
    }

    private static HorizonCombatSimulator.EnemySample sample(double cover, UnitType... enemies) {
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        for (UnitType enemy : enemies) {
            double ground = HorizonCombatSimulator.weightedGroundStrength(enemy, ALL_SMALL);
            double antiAir = HorizonCombatSimulator.weightedAntiAirStrength(enemy, ALL_SMALL);
            sample.add(enemy, ground * SwarmCover.groundMultiplier(enemy, cover), antiAir,
                    Math.max(ground, antiAir));
        }
        return sample;
    }

    private static HorizonCombatSimulator.PricedEngagement priced(UnitType[] ours, UnitType[] enemies,
                                                                  double cover) {
        HorizonCombatSimulator.FriendlyForce force = new HorizonCombatSimulator.FriendlyForce();
        for (UnitType unit : ours) {
            force.add(unit, CENTRE, 1.0, false);
        }
        return HorizonCombatSimulator.price(force, sample(cover, enemies));
    }

    private static double ratio(UnitType[] ours, UnitType[] enemies, double cover) {
        HorizonCombatSimulator.PricedEngagement priced = priced(ours, enemies, cover);
        return priced.getFriendlyGround() / priced.getEnemyGround();
    }

    private static Object verdict(UnitType[] ours, UnitType[] enemies, double cover) {
        HorizonCombatSimulator.PricedEngagement priced = priced(ours, enemies, cover);
        return HorizonCombatSimulator.selectResult(priced.getFriendlyGround(), priced.getFriendlyAir(),
                priced.getEnemyGround(), priced.getEnemyAntiAir(), priced.getEnemyEngaged(), false,
                TERRAN_ENGAGE_THRESHOLD);
    }
}
