package unit.squad.horizon;

import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import config.Config;
import info.GameState;
import info.TechProgression;
import info.tracking.DarkSwarmTracker;
import info.tracking.ObservedUnitTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.managed.Mutalisk;
import unit.managed.UnitRole;
import unit.squad.AirSquad;
import unit.squad.CombatSimulator.CombatResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class AirTurretEdgeOfFireTest {

    private static final UnitType TURRET = UnitType.Terran_Missile_Turret;
    private static final UnitType MARINE = UnitType.Terran_Marine;
    private static final UnitType MUTALISK = UnitType.Zerg_Mutalisk;
    private static final int REACH = AirTurretPricing.reach(TURRET);
    private static final double FULL_FLOCK_DISTANCE = REACH + AirTurretPricing.FLOCK_EXTENT
            + AirTurretPricing.ATTACKER_RANGE;
    private static final Map<UnitSizeType, Double> MUTALISKS = Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final double TERRAN_ENGAGE_THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Terran);
    private static final Position CENTRE = new Position(1000, 1000);
    private static final int FRAME = 10000;
    private static final int FLOCK = 4;
    private static final int MARINE_DISTANCE = 200;
    private static final int PULLED_BACK = 400;
    private static final int STACKED_FLOCK = 12;
    private static final int STACKED_MARINES = 4;
    private static final int STACKED_MARINE_DISTANCE = 100;
    private static final int[] STACKED_TURRETS = {483, 485, 547, 649};
    private static final UnitType[] OTHER_ENEMIES = {MARINE, UnitType.Terran_Goliath, UnitType.Terran_Bunker,
        UnitType.Zerg_Spore_Colony, UnitType.Protoss_Photon_Cannon, UnitType.Terran_Siege_Tank_Siege_Mode};

    private final boolean edgeSetting = Config.airTurretEdgeOfFire;

    @AfterEach
    void restoreSwitch() {
        Config.airTurretEdgeOfFire = edgeSetting;
    }

    /**
     * An air squad of Mutalisks and the enemy around it, built from real JBWAPI units and evaluated through the
     * simulator's own entry point.
     */
    private static final class Battle {
        private final TestUnits units = new TestUnits();
        private final Player self = units.player(0, true);
        private final Player enemy = units.player(1, false);
        private final ObservedUnitTracker tracker = new ObservedUnitTracker();
        private final AirSquad squad = new AirSquad();
        private final HorizonCombatSimulator sim = new HorizonCombatSimulator();

        Battle(int mutalisks) {
            units.setFrame(FRAME);
            for (int i = 0; i < mutalisks; i++) {
                Unit unit = units.unit(MUTALISK);
                units.setPlayer(unit, self);
                units.setHitPoints(unit, MUTALISK.maxHitPoints());
                units.setExists(unit, true);
                units.setPosition(unit, CENTRE.getX(), CENTRE.getY());
                squad.addUnit(new Mutalisk(units.game(), unit, UnitRole.FIGHT, null));
            }
        }

        Battle enemy(UnitType type, int dx, int dy) {
            Unit unit = units.unit(type);
            units.setPlayer(unit, enemy);
            units.setHitPoints(unit, type.maxHitPoints());
            units.setExists(unit, true);
            units.setCompleted(unit, true);
            units.setVisible(unit, self, true);
            units.setPosition(unit, CENTRE.getX() + dx, CENTRE.getY() + dy);
            tracker.onUnitShow(unit, FRAME, false);
            return this;
        }

        CombatResult evaluate() {
            return sim.evaluate(squad, Collections.emptyMap(), gameState());
        }

        HorizonCombatSimulator.DebugSnapshot snapshot() {
            return sim.getLastSnapshots().get(squad.getId());
        }

        private GameState gameState() {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                GameState state = (GameState) unsafeClass.getMethod("allocateInstance", Class.class)
                        .invoke(theUnsafe.get(null), GameState.class);
                set(state, "game", units.game());
                set(state, "self", self);
                set(state, "opponentRace", Race.Terran);
                set(state, "techProgression", new TechProgression());
                set(state, "observedUnitTracker", tracker);
                set(state, "darkSwarmTracker", new DarkSwarmTracker());
                return state;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private static void set(GameState state, String name, Object value) throws ReflectiveOperationException {
            Field field = GameState.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(state, value);
        }
    }

    private static HorizonCombatSimulator.FriendlyForce flock(int size) {
        HorizonCombatSimulator.FriendlyForce force = new HorizonCombatSimulator.FriendlyForce();
        for (int i = 0; i < size; i++) {
            force.add(MUTALISK, CENTRE, 1.0, false);
        }
        return force;
    }

    private static void addEnemy(HorizonCombatSimulator.EnemySample sample, UnitType type, double weight) {
        sample.add(type, HorizonCombatSimulator.weightedGroundStrength(type, MUTALISKS) * weight,
                HorizonCombatSimulator.weightedAntiAirStrength(type, MUTALISKS) * weight);
    }

    private static AirTurretPricing.Turret turret(double flockDistance) {
        return new AirTurretPricing.Turret(TURRET, new Position(CENTRE.getX() + (int) flockDistance, CENTRE.getY()),
                REACH, flockDistance, HorizonCombatSimulator.weightedGroundStrength(TURRET, MUTALISKS),
                HorizonCombatSimulator.weightedAntiAirStrength(TURRET, MUTALISKS), true);
    }

    private static CombatResult verdict(int mutalisks, HorizonCombatSimulator.EnemySample sample) {
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(flock(mutalisks), sample);
        return HorizonCombatSimulator.selectResult(priced.getFriendlyGround(), priced.getFriendlyAir(),
                priced.getEnemyGround(), priced.getEnemyAntiAir(), priced.getEnemyEngaged(), true,
                TERRAN_ENGAGE_THRESHOLD);
    }

    private static CombatResult stackedVerdict(boolean everyTurretInFull) {
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        for (int i = 0; i < STACKED_MARINES; i++) {
            addEnemy(sample, MARINE, 1.0);
        }
        List<AirTurretPricing.Turret> turrets = new ArrayList<>();
        for (int distance : STACKED_TURRETS) {
            turrets.add(turret(distance));
        }
        Position target = new Position(CENTRE.getX() - STACKED_MARINE_DISTANCE, CENTRE.getY());
        if (everyTurretInFull) {
            for (int i = 0; i < turrets.size(); i++) {
                addEnemy(sample, TURRET, 1.0);
            }
        } else {
            AirTurretPricing.price(turrets, target, sample, new HorizonCombatSimulator.DebugSnapshot());
        }
        return verdict(STACKED_FLOCK, sample);
    }

    @Test
    void throughEvaluateAVisibleTurretPulledBackFromReadsRetreatOnAndEngageOff() {
        Config.airTurretEdgeOfFire = true;
        Battle on = new Battle(FLOCK).enemy(MARINE, MARINE_DISTANCE, 0).enemy(MARINE, MARINE_DISTANCE, 16)
                .enemy(TURRET, PULLED_BACK, 0);
        assertEquals(RETREAT, on.evaluate());
        assertEquals(1, on.snapshot().getAirEdgeOfFire().size());

        Config.airTurretEdgeOfFire = false;
        Battle off = new Battle(FLOCK).enemy(MARINE, MARINE_DISTANCE, 0).enemy(MARINE, MARINE_DISTANCE, 16)
                .enemy(TURRET, PULLED_BACK, 0);
        assertEquals(ENGAGE, off.evaluate());
        assertTrue(off.snapshot().getAirEdgeOfFire().isEmpty());
        assertTrue(off.snapshot().getEnemyUnits().stream().noneMatch(e -> e.getType() == TURRET));
    }

    @Test
    void throughEvaluateTheSwitchOffKeepsATurretInsideTheFixedRadiusInFull() {
        Config.airTurretEdgeOfFire = false;
        Battle battle = new Battle(FLOCK).enemy(TURRET, 300, 0);
        battle.evaluate();
        HorizonCombatSimulator.UnitDebugEntry entry = battle.snapshot().getEnemyUnits().get(0);
        assertEquals(TURRET, entry.getType());
        assertEquals(HorizonCombatSimulator.weightedAntiAirStrength(TURRET, MUTALISKS), entry.getStrength(), 1e-9);
        assertNull(battle.snapshot().getAirCoverTarget());
    }

    @Test
    void throughEvaluateTheNearestTargetIsRecordedForTheDebugDraw() {
        Config.airTurretEdgeOfFire = true;
        Battle battle = new Battle(FLOCK).enemy(MARINE, MARINE_DISTANCE, 0).enemy(TURRET, PULLED_BACK, 0);
        battle.evaluate();
        assertEquals(new Position(CENTRE.getX() + MARINE_DISTANCE, CENTRE.getY()),
                battle.snapshot().getAirCoverTarget());
    }

    @Test
    void aTurretWeighsInFullWhileAMemberCouldBeHitOrHittingInsideItsReach() {
        assertEquals(1.0, AirTurretPricing.weight(FULL_FLOCK_DISTANCE, Double.POSITIVE_INFINITY, REACH));
        assertTrue(AirTurretPricing.weight(FULL_FLOCK_DISTANCE + 1, Double.POSITIVE_INFINITY, REACH) < 1.0);
        assertEquals(1.0, AirTurretPricing.weight(PULLED_BACK, Double.POSITIVE_INFINITY, REACH));
    }

    @Test
    void aTurretCoveringTheFightTargetPricesInFullWhereverTheFlockStands() {
        double coverReach = REACH + AirTurretPricing.ATTACKER_RANGE;
        for (double flockDistance : new double[]{100, 500, 700, 850}) {
            assertEquals(1.0, AirTurretPricing.weight(flockDistance, coverReach, REACH));
            assertEquals(1.0, AirTurretPricing.weight(flockDistance, 30, REACH));
        }
    }

    @Test
    void aFlockReadsRetreatAgainstMarinesUnderATurretItPulledBackFrom() {
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        addEnemy(sample, MARINE, 1.0);
        addEnemy(sample, MARINE, 1.0);
        Position marines = new Position(CENTRE.getX() + 300, CENTRE.getY());
        AirTurretPricing.price(Collections.singletonList(turret(600)), marines, sample,
                new HorizonCombatSimulator.DebugSnapshot());
        assertEquals(RETREAT, verdict(FLOCK, sample));
    }

    @Test
    void theWeightIsContinuousAndReachesNothingWhereTheSampleEnds() {
        double radius = AirTurretPricing.sampleRadius(REACH);
        double step = 1.0 / AirTurretPricing.TAPER + 1e-9;
        for (double targetOffset : new double[]{0, 100, HorizonCombatSimulator.MAX_ENGAGEMENT_RADIUS}) {
            double previous = weightWithTargetToward(0, targetOffset);
            for (double d = 1; d <= radius + 10; d++) {
                double weight = weightWithTargetToward(d, targetOffset);
                assertTrue(Math.abs(weight - previous) <= step, "step at " + d);
                previous = weight;
            }
            assertEquals(0.0, weightWithTargetToward(radius, targetOffset));
        }
    }

    private static double weightWithTargetToward(double flockDistance, double targetOffset) {
        double cover = Math.abs(flockDistance - targetOffset);
        return AirTurretPricing.weight(flockDistance, cover, REACH);
    }

    @Test
    void aTurretAtTheSampleEdgeAddsNothingAndJustInsideAddsAlmostNothing() {
        double radius = AirTurretPricing.sampleRadius(REACH);
        Position target = new Position(CENTRE.getX() + (int) HorizonCombatSimulator.MAX_ENGAGEMENT_RADIUS,
                CENTRE.getY());
        HorizonCombatSimulator.EnemySample atEdge = new HorizonCombatSimulator.EnemySample();
        AirTurretPricing.price(Collections.singletonList(turret(radius)), target, atEdge,
                new HorizonCombatSimulator.DebugSnapshot());
        assertEquals(0.0, atEdge.antiAirTotal());

        HorizonCombatSimulator.EnemySample inside = new HorizonCombatSimulator.EnemySample();
        AirTurretPricing.price(Collections.singletonList(turret(radius - 1)), target, inside,
                new HorizonCombatSimulator.DebugSnapshot());
        double full = HorizonCombatSimulator.weightedAntiAirStrength(TURRET, MUTALISKS);
        assertTrue(inside.antiAirTotal() > 0 && inside.antiAirTotal() <= full / AirTurretPricing.TAPER + 1e-9);
    }

    @Test
    void stackedDistantTurretsThatCoverNothingTheFlockFightsDoNotFlipAWinningRead() {
        assertEquals(RETREAT, stackedVerdict(true));
        assertEquals(ENGAGE, stackedVerdict(false));
    }

    @Test
    void withNoTurretNearTheVerdictIsUnchanged() {
        HorizonCombatSimulator.EnemySample with = new HorizonCombatSimulator.EnemySample();
        HorizonCombatSimulator.EnemySample without = new HorizonCombatSimulator.EnemySample();
        for (HorizonCombatSimulator.EnemySample sample : new HorizonCombatSimulator.EnemySample[]{with, without}) {
            addEnemy(sample, MARINE, 1.0);
            addEnemy(sample, MARINE, 1.0);
        }
        AirTurretPricing.price(Collections.singletonList(turret(AirTurretPricing.sampleRadius(REACH))), CENTRE,
                with, new HorizonCombatSimulator.DebugSnapshot());
        assertEquals(ENGAGE, verdict(FLOCK, without));
        assertEquals(verdict(FLOCK, without), verdict(FLOCK, with));
        assertEquals(without.antiAirTotal(), with.antiAirTotal());
    }

    @Test
    void onlyATurretAgainstAnAirSquadWithTheSwitchOnIsPricedByCover() {
        assertTrue(HorizonCombatSimulator.pricedAtAirEdgeOfFire(TURRET, true, true));
        assertFalse(HorizonCombatSimulator.pricedAtAirEdgeOfFire(TURRET, true, false));
        assertFalse(HorizonCombatSimulator.pricedAtAirEdgeOfFire(TURRET, false, true));
        for (UnitType type : OTHER_ENEMIES) {
            assertFalse(HorizonCombatSimulator.pricedAtAirEdgeOfFire(type, true, true));
        }
        assertEquals(TURRET.airWeapon().maxRange(), REACH);
    }
}
