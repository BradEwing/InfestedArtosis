package unit.squad.horizon;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.squad.CombatSimulator.CombatResult;

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
    private static final Position CENTRE = AirBattle.CENTRE;
    private static final int FRAME = AirBattle.FRAME;
    private static final int FLOCK = 4;
    private static final int MARINE_DISTANCE = 200;
    private static final int PULLED_BACK = 400;
    private static final int STACKED_FLOCK = 12;
    private static final int STACKED_MARINES = 4;
    private static final int STACKED_MARINE_DISTANCE = 100;
    private static final int[] STACKED_TURRETS = {483, 485, 547, 649};
    private static final int FAR_TURRET = 600;
    private static final int UNDER_FAR_TURRET = 300;
    private static final int CENTRE_LAG = 300;
    private static final int BEYOND_SAMPLE = 1000;
    private static final UnitType[] OTHER_ENEMIES = {MARINE, UnitType.Terran_Goliath, UnitType.Terran_Bunker,
        UnitType.Zerg_Spore_Colony, UnitType.Protoss_Photon_Cannon, UnitType.Terran_Siege_Tank_Siege_Mode};

    private final boolean edgeSetting = Config.airTurretEdgeOfFire;

    @AfterEach
    void restoreSwitch() {
        Config.airTurretEdgeOfFire = edgeSetting;
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

    private static void price(AirTurretPricing.Turret turret, List<AirTurretPricing.Target> targets,
                              HorizonCombatSimulator.EnemySample sample) {
        AirTurretPricing.price(Collections.singletonList(turret), targets, sample,
                new HorizonCombatSimulator.DebugSnapshot());
    }

    private static double turretStrength(AirBattle battle) {
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
            AirTurretPricing.price(turrets, Collections.singletonList(target(-STACKED_MARINE_DISTANCE)), sample,
                    new HorizonCombatSimulator.DebugSnapshot());
        }
        return verdict(STACKED_FLOCK, sample);
    }

    @Test
    void throughEvaluateAVisibleTurretPulledBackFromReadsRetreatOnAndEngageOff() {
        Config.airTurretEdgeOfFire = true;
        AirBattle on = new AirBattle(FLOCK).enemy(MARINE, MARINE_DISTANCE, 0).enemy(MARINE, MARINE_DISTANCE, 16)
                .enemy(TURRET, PULLED_BACK, 0);
        assertEquals(RETREAT, on.evaluate());
        assertEquals(1, on.snapshot().getAirEdgeOfFire().size());

        Config.airTurretEdgeOfFire = false;
        AirBattle off = new AirBattle(FLOCK).enemy(MARINE, MARINE_DISTANCE, 0).enemy(MARINE, MARINE_DISTANCE, 16)
                .enemy(TURRET, PULLED_BACK, 0);
        assertEquals(ENGAGE, off.evaluate());
        assertTrue(off.snapshot().getAirEdgeOfFire().isEmpty());
        assertTrue(off.snapshot().getEnemyUnits().stream().noneMatch(e -> e.getType() == TURRET));
    }

    @Test
    void throughEvaluateTheSwitchOffKeepsATurretInsideTheFixedRadiusInFull() {
        Config.airTurretEdgeOfFire = false;
        AirBattle battle = new AirBattle(FLOCK).enemy(TURRET, 300, 0);
        battle.evaluate();
        HorizonCombatSimulator.UnitDebugEntry entry = battle.snapshot().getEnemyUnits().get(0);
        assertEquals(TURRET, entry.getType());
        assertEquals(HorizonCombatSimulator.weightedAntiAirStrength(TURRET, MUTALISKS), entry.getStrength(), 1e-9);
        assertTrue(battle.snapshot().getAirCoverTargets().isEmpty());
    }

    @Test
    void throughEvaluateTheCoveringTargetIsRecordedForTheDebugDraw() {
        Config.airTurretEdgeOfFire = true;
        AirBattle battle = new AirBattle(FLOCK).enemy(MARINE, MARINE_DISTANCE, 0).enemy(TURRET, PULLED_BACK, 0);
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
            AirBattle battle = new AirBattle(FLOCK).enemy(MARINE, dx, 0).enemy(TURRET, FAR_TURRET, 0);
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
        AirBattle depot = new AirBattle(FLOCK).enemy(UnitType.Terran_Supply_Depot, UNDER_FAR_TURRET, 0)
                .enemy(TURRET, FAR_TURRET, 0);
        depot.evaluate();
        double flockOnly = AirTurretPricing.weight(FAR_TURRET, Double.POSITIVE_INFINITY, REACH);
        assertEquals(fullTurret() * flockOnly, turretStrength(depot), 1e-9);
        assertTrue(flockOnly < 0.5);

        AirBattle scv = new AirBattle(FLOCK).enemy(UnitType.Terran_SCV, UNDER_FAR_TURRET, 0).enemy(TURRET, FAR_TURRET, 0);
        scv.evaluate();
        assertEquals(fullTurret() * flockOnly, turretStrength(scv), 1e-9);

        AirBattle marine = new AirBattle(FLOCK).enemy(MARINE, UNDER_FAR_TURRET, 0).enemy(TURRET, FAR_TURRET, 0);
        marine.evaluate();
        assertEquals(fullTurret(), turretStrength(marine), 1e-9);
    }

    @Test
    void throughEvaluateATurretWeighsInFullWhileAMemberIsInReachWhereverTheSmoothedCentreLags() {
        Config.airTurretEdgeOfFire = true;
        int turretDistance = CENTRE_LAG + REACH - 20;
        AirBattle lagged = new AirBattle(FLOCK).enemy(TURRET, turretDistance, 0);
        assertEquals(CENTRE, lagged.squad.getCenter());
        for (int i = 0; i < FLOCK; i++) {
            lagged.move(i, CENTRE_LAG, 0);
        }
        lagged.evaluate();
        assertEquals(CENTRE, lagged.snapshot().getSquadCenter());
        assertTrue(AirTurretPricing.weight(turretDistance, Double.POSITIVE_INFINITY, REACH) < 1.0);
        assertEquals(fullTurret(), turretStrength(lagged), 1e-9);
    }

    @Test
    void throughEvaluateOneMemberInReachOfATurretPastTheSampleRadiusWeighsItInFull() {
        Config.airTurretEdgeOfFire = true;
        AirBattle straggler = new AirBattle(FLOCK).enemy(TURRET, BEYOND_SAMPLE, 0);
        assertEquals(CENTRE, straggler.squad.getCenter());
        straggler.move(0, BEYOND_SAMPLE - REACH + 10, 0);
        straggler.evaluate();
        assertTrue(BEYOND_SAMPLE > AirTurretPricing.sampleRadius(REACH));
        assertEquals(fullTurret(), turretStrength(straggler), 1e-9);

        AirBattle together = new AirBattle(FLOCK).enemy(TURRET, BEYOND_SAMPLE, 0);
        together.evaluate();
        assertEquals(0.0, turretStrength(together));
    }

    @Test
    void theFlockDistanceIsTheNearestMemberOrTheCentreWithNoMember() {
        Position turretPosition = new Position(CENTRE.getX() + FAR_TURRET, CENTRE.getY());
        List<Position> members = new ArrayList<>();
        assertEquals(FAR_TURRET, AirTurretPricing.flockDistance(members, turretPosition, FAR_TURRET));
        members.add(CENTRE);
        members.add(new Position(CENTRE.getX() + 400, CENTRE.getY()));
        assertEquals(FAR_TURRET - 400, AirTurretPricing.flockDistance(members, turretPosition, FAR_TURRET), 1e-9);
        assertTrue(AirTurretPricing.sampled(BEYOND_SAMPLE, FULL_FLOCK_DISTANCE, REACH));
        assertFalse(AirTurretPricing.sampled(BEYOND_SAMPLE, FULL_FLOCK_DISTANCE + AirTurretPricing.TAPER, REACH));
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
}
