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
import unit.squad.SquadStatus;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final int HELD_TURRET_DISTANCE = 560;
    private static final int NEAR_FULL_TURRET_DISTANCE = 424;
    private static final int FAR_TURRET = 600;
    private static final int UNDER_FAR_TURRET = 300;
    private static final double AIR = 100;
    private static final UnitType[] OTHER_ENEMIES = {MARINE, UnitType.Terran_Goliath, UnitType.Terran_Bunker,
        UnitType.Zerg_Spore_Colony, UnitType.Protoss_Photon_Cannon, UnitType.Terran_Siege_Tank_Siege_Mode};

    private final boolean edgeSetting = Config.airTurretEdgeOfFire;
    private final boolean holdSetting = Config.airVerdictHold;

    @AfterEach
    void restoreSwitch() {
        Config.airTurretEdgeOfFire = edgeSetting;
        Config.airVerdictHold = holdSetting;
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

    private static AirTurretPricing.Target target(int dx) {
        return new AirTurretPricing.Target(new Position(CENTRE.getX() + dx, CENTRE.getY()), Math.abs(dx));
    }

    private static double price(AirTurretPricing.Turret turret, List<AirTurretPricing.Target> targets,
                                HorizonCombatSimulator.EnemySample sample) {
        return AirTurretPricing.price(Collections.singletonList(turret), targets, Collections.singletonList(CENTRE),
                sample, new HorizonCombatSimulator.DebugSnapshot());
    }

    private static double turretStrength(Battle battle) {
        return battle.snapshot().getEnemyUnits().stream().filter(e -> e.getType() == TURRET)
                .mapToDouble(HorizonCombatSimulator.UnitDebugEntry::getStrength).sum();
    }

    private static double fullTurret() {
        return HorizonCombatSimulator.weightedAntiAirStrength(TURRET, MUTALISKS);
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
        if (everyTurretInFull) {
            for (int i = 0; i < turrets.size(); i++) {
                addEnemy(sample, TURRET, 1.0);
            }
        } else {
            AirTurretPricing.price(turrets, Collections.singletonList(target(-STACKED_MARINE_DISTANCE)),
                    Collections.singletonList(CENTRE), sample, new HorizonCombatSimulator.DebugSnapshot());
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
        assertTrue(battle.snapshot().getAirCoverTargets().isEmpty());
    }

    @Test
    void throughEvaluateTheCoveringTargetIsRecordedForTheDebugDraw() {
        Config.airTurretEdgeOfFire = true;
        Battle battle = new Battle(FLOCK).enemy(MARINE, MARINE_DISTANCE, 0).enemy(TURRET, PULLED_BACK, 0);
        battle.evaluate();
        Position turretPosition = new Position(CENTRE.getX() + PULLED_BACK, CENTRE.getY());
        assertEquals(new Position(CENTRE.getX() + MARINE_DISTANCE, CENTRE.getY()),
                battle.snapshot().getAirCoverTargets().get(turretPosition));
    }

    @Test
    void throughEvaluateAMarineMovingAcrossTheOldWindowEdgeMovesTheTurretWithoutAStep() {
        Config.airTurretEdgeOfFire = true;
        double step = 8 * fullTurret() / AirTurretPricing.TAPER + 1e-9;
        double previous = -1;
        for (int dx = 240; dx <= 680; dx += 8) {
            Battle battle = new Battle(FLOCK).enemy(MARINE, dx, 0).enemy(TURRET, FAR_TURRET, 0);
            battle.evaluate();
            double strength = turretStrength(battle);
            if (previous >= 0) {
                assertTrue(Math.abs(strength - previous) <= step, "step with the Marine at " + dx);
            }
            previous = strength;
        }
    }

    @Test
    void throughEvaluateADepotNextToAFarTurretDoesNotMakeItCoverAndAMarineDoes() {
        Config.airTurretEdgeOfFire = true;
        Battle depot = new Battle(FLOCK).enemy(UnitType.Terran_Supply_Depot, UNDER_FAR_TURRET, 0)
                .enemy(TURRET, FAR_TURRET, 0);
        depot.evaluate();
        double flockOnly = AirTurretPricing.weight(FAR_TURRET, Double.POSITIVE_INFINITY, REACH);
        assertEquals(fullTurret() * flockOnly, turretStrength(depot), 1e-9);
        assertTrue(flockOnly < 0.5);

        Battle scv = new Battle(FLOCK).enemy(UnitType.Terran_SCV, UNDER_FAR_TURRET, 0).enemy(TURRET, FAR_TURRET, 0);
        scv.evaluate();
        assertEquals(fullTurret() * flockOnly, turretStrength(scv), 1e-9);

        Battle marine = new Battle(FLOCK).enemy(MARINE, UNDER_FAR_TURRET, 0).enemy(TURRET, FAR_TURRET, 0);
        marine.evaluate();
        assertEquals(fullTurret(), turretStrength(marine), 1e-9);
    }

    @Test
    void onlyAnArmedNonWorkerInSightOrFreshIsACoverCandidate() {
        int fresh = 120;
        assertTrue(AirTurretPricing.coverCandidate(MARINE, true, 0, fresh));
        assertTrue(AirTurretPricing.coverCandidate(UnitType.Terran_Goliath, false, fresh, fresh));
        assertTrue(AirTurretPricing.coverCandidate(UnitType.Terran_Bunker, true, 0, fresh));
        assertFalse(AirTurretPricing.coverCandidate(MARINE, false, fresh + 1, fresh));
        assertFalse(AirTurretPricing.coverCandidate(UnitType.Terran_SCV, true, 0, fresh));
        assertFalse(AirTurretPricing.coverCandidate(UnitType.Terran_Supply_Depot, true, 0, fresh));
        assertFalse(AirTurretPricing.coverCandidate(UnitType.Terran_Engineering_Bay, true, 0, fresh));
        assertFalse(AirTurretPricing.coverCandidate(UnitType.Terran_Academy, true, 0, fresh));
        assertFalse(AirTurretPricing.coverCandidate(TURRET, true, 0, fresh));
    }

    @Test
    void theCoverGapIsTheBestCandidateAndFadesPastTheTargetRadius() {
        Position turretPosition = new Position(CENTRE.getX() + FAR_TURRET, CENTRE.getY());
        List<AirTurretPricing.Target> targets = new ArrayList<>();
        targets.add(target(-100));
        assertTrue(AirTurretPricing.coverGap(turretPosition, REACH, targets) >= AirTurretPricing.TAPER);
        targets.add(target(UNDER_FAR_TURRET));
        assertEquals(0.0, AirTurretPricing.coverGap(turretPosition, REACH, targets));
        AirTurretPricing.Target outside = new AirTurretPricing.Target(turretPosition,
                AirTurretPricing.TARGET_WINDOW);
        assertEquals(AirTurretPricing.TAPER,
                AirTurretPricing.coverGap(turretPosition, REACH, Collections.singletonList(outside)));
        assertEquals(Double.POSITIVE_INFINITY,
                AirTurretPricing.coverGap(turretPosition, REACH, Collections.emptyList()));
    }

    @Test
    void aTurretWeighsInFullWhileAMemberCouldBeHitOrHittingInsideItsReach() {
        assertEquals(1.0, AirTurretPricing.weight(FULL_FLOCK_DISTANCE, Double.POSITIVE_INFINITY, REACH));
        assertTrue(AirTurretPricing.weight(FULL_FLOCK_DISTANCE + 1, Double.POSITIVE_INFINITY, REACH) < 1.0);
        assertEquals(1.0, AirTurretPricing.weight(PULLED_BACK, Double.POSITIVE_INFINITY, REACH));
    }

    @Test
    void aTurretCoveringTheFightTargetPricesInFullWhereverTheFlockStands() {
        for (double flockDistance : new double[]{100, 500, 700, 850}) {
            assertEquals(1.0, AirTurretPricing.weight(flockDistance, 0, REACH));
        }
    }

    @Test
    void aFlockReadsRetreatAgainstMarinesUnderATurretItPulledBackFrom() {
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        addEnemy(sample, MARINE, 1.0);
        addEnemy(sample, MARINE, 1.0);
        price(turret(FAR_TURRET), Collections.singletonList(target(UNDER_FAR_TURRET)), sample);
        assertEquals(RETREAT, verdict(FLOCK, sample));
    }

    @Test
    void theWeightIsContinuousAndReachesNothingWhereTheSampleEnds() {
        double radius = AirTurretPricing.sampleRadius(REACH);
        double step = 1.0 / AirTurretPricing.TAPER + 1e-9;
        for (double targetOffset : new double[]{0, 100, AirTurretPricing.TARGET_RADIUS,
            AirTurretPricing.TARGET_WINDOW}) {
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
        Position turretPosition = new Position(CENTRE.getX() + (int) flockDistance, CENTRE.getY());
        double cover = AirTurretPricing.coverGap(turretPosition, REACH,
                Collections.singletonList(target((int) targetOffset)));
        return AirTurretPricing.weight(flockDistance, cover, REACH);
    }

    @Test
    void aTurretAtTheSampleEdgeAddsNothingAndJustInsideAddsAlmostNothing() {
        double radius = AirTurretPricing.sampleRadius(REACH);
        List<AirTurretPricing.Target> targets = Collections.singletonList(
                target((int) HorizonCombatSimulator.MAX_ENGAGEMENT_RADIUS));
        HorizonCombatSimulator.EnemySample atEdge = new HorizonCombatSimulator.EnemySample();
        price(turret(radius), targets, atEdge);
        assertEquals(0.0, atEdge.antiAirTotal());

        HorizonCombatSimulator.EnemySample inside = new HorizonCombatSimulator.EnemySample();
        price(turret(radius - 1), targets, inside);
        double full = fullTurret();
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
        price(turret(AirTurretPricing.sampleRadius(REACH)), Collections.singletonList(target(0)), with);
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

    private static Battle heldBattle(SquadStatus status) {
        Battle battle = new Battle(FLOCK).enemy(MARINE, -MARINE_DISTANCE, 0).enemy(MARINE, -MARINE_DISTANCE, 16)
                .enemy(TURRET, HELD_TURRET_DISTANCE, -40).enemy(TURRET, HELD_TURRET_DISTANCE, 0)
                .enemy(TURRET, HELD_TURRET_DISTANCE, 40);
        battle.squad.setStatus(status);
        return battle;
    }

    @Test
    void throughEvaluateAFightingFlockHoldsAgainstARetreatOnlyUncoveringTurretsTip() {
        Config.airTurretEdgeOfFire = true;
        Config.airVerdictHold = false;
        assertEquals(RETREAT, heldBattle(SquadStatus.FIGHT).evaluate());

        Config.airVerdictHold = true;
        Battle held = heldBattle(SquadStatus.FIGHT);
        assertEquals(ENGAGE, held.evaluate());
        assertEquals(FRAME, held.snapshot().getFightBandSinceFrame());
        assertTrue(held.snapshot().getOverallRatio() < TERRAN_ENGAGE_THRESHOLD);
    }

    @Test
    void throughEvaluateUncoveringTurretsStillKeepARetreatingFlockOut() {
        Config.airTurretEdgeOfFire = true;
        Config.airVerdictHold = true;
        Battle retreating = heldBattle(SquadStatus.RETREAT);
        assertEquals(RETREAT, retreating.evaluate());
        assertEquals(-1, retreating.snapshot().getFightBandSinceFrame());
    }

    @Test
    void throughEvaluateTheHoldReleasesOnceATurretCoversTheFlock() {
        Config.airTurretEdgeOfFire = true;
        Config.airVerdictHold = true;
        Battle covered = new Battle(FLOCK).enemy(MARINE, MARINE_DISTANCE, 0).enemy(MARINE, MARINE_DISTANCE, 16)
                .enemy(TURRET, PULLED_BACK, 0);
        covered.squad.setStatus(SquadStatus.FIGHT);
        assertEquals(RETREAT, covered.evaluate());
        assertEquals(-1, covered.snapshot().getFightBandSinceFrame());
    }

    @Test
    void throughEvaluateTheHoldDoesNotIgnoreATurretAtNearlyFullWeight() {
        Config.airTurretEdgeOfFire = true;
        Config.airVerdictHold = true;
        Battle nearFull = new Battle(FLOCK).enemy(MARINE, -MARINE_DISTANCE, 0)
                .enemy(TURRET, NEAR_FULL_TURRET_DISTANCE, -40).enemy(TURRET, NEAR_FULL_TURRET_DISTANCE, 40);
        nearFull.squad.setStatus(SquadStatus.FIGHT);
        assertEquals(RETREAT, nearFull.evaluate());
        assertEquals(-1, nearFull.snapshot().getFightBandSinceFrame());
    }

    @Test
    void aTurretAtNearlyFullWeightOrInReachOfAMemberIsNotHoldable() {
        double nearFull = AirTurretPricing.weight(NEAR_FULL_TURRET_DISTANCE, Double.POSITIVE_INFINITY, REACH);
        assertTrue(nearFull > 0.95 && nearFull < 1.0);
        Position far = new Position(CENTRE.getX() + FAR_TURRET, CENTRE.getY());
        List<Position> atCentre = Collections.singletonList(CENTRE);
        assertFalse(AirTurretPricing.holdable(nearFull, far, REACH, atCentre));
        assertTrue(AirTurretPricing.holdable(AirTurretPricing.HOLD_MAX_WEIGHT, far, REACH, atCentre));
        List<Position> straggler = Collections.singletonList(
                new Position(far.getX() - REACH - AirTurretPricing.EDGE_MARGIN, far.getY()));
        assertFalse(AirTurretPricing.holdable(0.3, far, REACH, straggler));

        assertEquals(0.0, price(turret(NEAR_FULL_TURRET_DISTANCE), Collections.emptyList(),
                new HorizonCombatSimulator.EnemySample()));
        assertTrue(price(turret(FAR_TURRET), Collections.emptyList(), new HorizonCombatSimulator.EnemySample()) > 0);
    }

    @Test
    void theHoldTurnsARetreatInsideTheBandIntoEngage() {
        assertEquals(ENGAGE, AirTurretPricing.hold(true, true, RETREAT, AIR, 90, 40, TERRAN_ENGAGE_THRESHOLD));
        assertEquals(ENGAGE, AirTurretPricing.hold(true, true, RETREAT, AIR, 40, 40, TERRAN_ENGAGE_THRESHOLD));
    }

    @Test
    void theHoldReleasesWhenTheCoveringReadLosesOrNoTurretIsTapered() {
        assertEquals(RETREAT, AirTurretPricing.hold(true, true, RETREAT, AIR, 90, 10, TERRAN_ENGAGE_THRESHOLD));
        assertEquals(RETREAT, AirTurretPricing.hold(true, true, RETREAT, AIR, 90, 0, TERRAN_ENGAGE_THRESHOLD));
    }

    @Test
    void theHoldActsOnlyOnARetreatReadOfAFightingSquadWithTheSwitchOn() {
        assertEquals(RETREAT, AirTurretPricing.hold(false, true, RETREAT, AIR, 90, 40, TERRAN_ENGAGE_THRESHOLD));
        assertEquals(RETREAT, AirTurretPricing.hold(true, false, RETREAT, AIR, 90, 40, TERRAN_ENGAGE_THRESHOLD));
        for (CombatResult raw : CombatResult.values()) {
            if (raw != RETREAT) {
                assertEquals(raw, AirTurretPricing.hold(true, true, raw, AIR, 90, 40, TERRAN_ENGAGE_THRESHOLD));
            }
        }
    }
}
