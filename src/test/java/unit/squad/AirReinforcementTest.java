package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirReinforcementTest {

    private static final Position FROM = new Position(1000, 2000);
    private static final Position GOAL = new Position(3000, 2000);
    private static final Position MIDWAY = new Position(2000, 2000);
    private static final Predicate<Position> ANYWHERE = point -> true;

    private static AirHarassTargeting.AirThreat threat(int id, UnitType type, Position position) {
        return AirHarassTargeting.AirThreat.of(id, type, position,
                AirHarassTargeting.airRange(type, WeaponType::maxRange));
    }

    private static Map<UnitType, Integer> composition(UnitType type, int count) {
        Map<UnitType, Integer> composition = new HashMap<>();
        composition.put(type, count);
        return composition;
    }

    private static AirReinforcement.Candidate<String> candidate(String name, SquadStatus status, Position center) {
        return new AirReinforcement.Candidate<>(name, status, center);
    }

    private static void assertEveryLegOutside(Position from, List<Position> path,
                                              List<AirHarassTargeting.AirThreat> threats) {
        Position at = from;
        for (Position point : path) {
            assertTrue(AirHarassTargeting.segmentClear(at, point, threats), at + " -> " + point);
            at = point;
        }
    }

    @Test
    void aNewMutaJoinsAHarassSquadNearIt() {
        assertTrue(SquadManager.mayJoinAirSquadAt(SquadStatus.HARASS, SquadManager.AIR_JOIN_DISTANCE - 1));
        assertFalse(SquadManager.mayJoinAirSquadAt(SquadStatus.HARASS, SquadManager.AIR_JOIN_DISTANCE));
        assertEquals(SquadManager.ReinforcementPath.JOIN_HARASS,
                SquadManager.reinforcementPath(SquadStatus.HARASS, false, false));
        assertTrue(AirReinforcement.mayReinforce(SquadStatus.HARASS, composition(UnitType.Zerg_Mutalisk, 1)));
    }

    @Test
    void aHarassSquadTakesMutalisksOnly() {
        assertFalse(AirReinforcement.mayReinforce(SquadStatus.HARASS, composition(UnitType.Zerg_Guardian, 1)));
        assertFalse(AirReinforcement.mayReinforce(SquadStatus.HARASS, composition(UnitType.Zerg_Scourge, 2)));
        assertTrue(AirReinforcement.mayReinforce(SquadStatus.FIGHT, composition(UnitType.Zerg_Guardian, 1)));
    }

    @Test
    void aNewMutaAtHomeIsRoutedToAHarassSquad() {
        AirReinforcement.Route<String> route = AirReinforcement.choose(FROM,
                Arrays.asList(candidate("rally", SquadStatus.RALLY, new Position(1100, 2000)),
                        candidate("harass", SquadStatus.HARASS, GOAL)),
                Collections.emptyList(), ANYWHERE);

        assertNotNull(route);
        assertEquals("harass", route.getTarget());
        assertEquals(Collections.singletonList(GOAL), route.getPath());
    }

    @Test
    void aMutaJoiningAHarassAddsItsHitPointsToTheStart() {
        AirHarassState state = new AirHarassState(9000, 480);

        state.addStartHitPoints(120);

        assertEquals(600, state.getStartHitPoints());
        assertEquals(0.0, AirHarassEvaluator.hpLossFraction(state.getStartHitPoints(), 600));
    }

    @Test
    void aTurretOnTheDirectLineIsRefusedAndADetourIsTaken() {
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(1, UnitType.Terran_Missile_Turret, MIDWAY));
        assertFalse(AirHarassTargeting.segmentClear(FROM, GOAL, threats));

        List<Position> path = AirReinforcement.safePath(FROM, GOAL, threats, ANYWHERE);

        assertNotNull(path);
        assertTrue(path.size() >= 2);
        assertTrue(path.get(path.size() - 1).getDistance(GOAL) <= AirReinforcement.ARRIVAL_RING + 1);
        assertEveryLegOutside(FROM, path, threats);
    }

    @Test
    void aBunkerOnTheDirectLineIsAlsoRoundedByADetour() {
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(1, UnitType.Terran_Bunker, MIDWAY));

        List<Position> path = AirReinforcement.safePath(FROM, GOAL, threats, ANYWHERE);

        assertNotNull(path);
        assertTrue(path.size() >= 2);
        assertEveryLegOutside(FROM, path, threats);
    }

    @Test
    void aClearLineIsFlownStraight() {
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(1, UnitType.Terran_Missile_Turret, new Position(2000, 2800)));

        assertEquals(Collections.singletonList(GOAL), AirReinforcement.safePath(FROM, GOAL, threats, ANYWHERE));
    }

    @Test
    void noPathIsFoundWhenEveryDetourIsOffTheMap() {
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(1, UnitType.Terran_Missile_Turret, MIDWAY));

        assertNull(AirReinforcement.safePath(FROM, GOAL, threats, point -> false));
    }

    @Test
    void noPathIsFoundOutOfARingOfTurrets() {
        List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            threats.add(threat(i, UnitType.Terran_Missile_Turret,
                    new Position(FROM.getX() + (int) Math.round(Math.cos(angle) * 400),
                            FROM.getY() + (int) Math.round(Math.sin(angle) * 400))));
        }

        assertNull(AirReinforcement.safePath(FROM, GOAL, threats, ANYWHERE));
    }

    @Test
    void aTurretCoveringTheTargetSquadIsTheFightItIsInAndIsNotAvoided() {
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(1, UnitType.Terran_Missile_Turret, new Position(GOAL.getX() + 100, GOAL.getY())));

        assertEquals(Collections.singletonList(GOAL), AirReinforcement.safePath(FROM, GOAL, threats, ANYWHERE));
    }

    @Test
    void aThreatCoveringTheTargetStillBlocksItsFarSide() {
        Position east = new Position(4000, 2000);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(1, UnitType.Terran_Missile_Turret, new Position(GOAL.getX() + 150, GOAL.getY())));

        List<Position> path = AirReinforcement.safePath(east, GOAL, threats, ANYWHERE);

        assertNotNull(path);
        assertTrue(path.size() >= 2);
        Position at = east;
        for (Position point : path) {
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                Position sample = new Position((int) Math.round(at.getX() + (point.getX() - at.getX()) * t),
                        (int) Math.round(at.getY() + (point.getY() - at.getY()) * t));
                assertTrue(!threats.get(0).covers(sample, AirHarassTargeting.padding())
                        || sample.getDistance(GOAL) < AirReinforcement.ARRIVAL_DISTANCE, sample.toString());
            }
            at = point;
        }
    }

    @Test
    void aSquadStandingInAntiAirHasNoPath() {
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(1, UnitType.Terran_Goliath, new Position(FROM.getX() + 50, FROM.getY())));

        assertNull(AirReinforcement.safePath(FROM, GOAL, threats, ANYWHERE));
    }

    @Test
    void aSearchOverManyThreatsStillReturnsASafePath() {
        List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();
        int id = 0;
        for (int x = 1500; x <= 2500; x += 250) {
            for (int y = 1000; y <= 2600; y += 400) {
                threats.add(threat(id++, UnitType.Terran_Missile_Turret, new Position(x, y)));
            }
        }
        for (int i = 0; i < 40; i++) {
            threats.add(threat(id++, UnitType.Terran_Marine, new Position(6000 + i * 10, 6000)));
        }

        List<Position> path = AirReinforcement.safePath(FROM, GOAL, threats, ANYWHERE);

        assertNotNull(path);
        assertEveryLegOutside(FROM, path, threats);
    }

    @Test
    void onlyARallyingAirSquadWithMembersSeeksATarget() {
        assertTrue(AirReinforcement.seeksReinforcementTarget(SquadStatus.RALLY, true, 1));
        assertFalse(AirReinforcement.seeksReinforcementTarget(SquadStatus.RALLY, true, 0));
        assertFalse(AirReinforcement.seeksReinforcementTarget(SquadStatus.RALLY, false, 4));
        assertFalse(AirReinforcement.seeksReinforcementTarget(SquadStatus.FIGHT, true, 4));
    }

    @Test
    void theNearestSquadWithNoSafePathIsPassedOverForAFartherOne() {
        Position blockedGoal = new Position(2000, 2000);
        Position openGoal = new Position(1000, 3400);
        List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            threats.add(threat(i, UnitType.Terran_Missile_Turret,
                    new Position(blockedGoal.getX() + (int) Math.round(Math.cos(angle) * 500),
                            blockedGoal.getY() + (int) Math.round(Math.sin(angle) * 500))));
        }
        assertNull(AirReinforcement.safePath(FROM, blockedGoal, threats, ANYWHERE));

        AirReinforcement.Route<String> route = AirReinforcement.choose(FROM,
                Arrays.asList(candidate("near", SquadStatus.FIGHT, blockedGoal),
                        candidate("far", SquadStatus.RETREAT, openGoal)),
                threats, ANYWHERE);

        assertNotNull(route);
        assertEquals("far", route.getTarget());
    }

    @Test
    void noRouteWithoutAnActiveCandidate() {
        assertNull(AirReinforcement.choose(FROM,
                Collections.singletonList(candidate("rally", SquadStatus.RALLY, GOAL)),
                Collections.emptyList(), ANYWHERE));
    }

    @Test
    void theMoveOutRuleAppliesOnlyWithNoActiveAirSquad() {
        assertTrue(AirReinforcement.moveOutRuleApplies(false));
        assertFalse(AirReinforcement.moveOutRuleApplies(true));

        int waiting = AirReinforcement.launchThreshold(SquadManager.AIR_MOVE_OUT_UNITS, SquadStatus.RALLY, true);
        int alone = AirReinforcement.launchThreshold(SquadManager.AIR_MOVE_OUT_UNITS, SquadStatus.RALLY, false);

        assertEquals(SquadManager.SquadAction.RALLY, SquadManager.chooseSquadAction(false, false, 6, waiting,
                SquadStatus.RALLY, false, 0));
        assertEquals(SquadManager.SquadAction.LAUNCH, SquadManager.chooseSquadAction(false, false, 6, alone,
                SquadStatus.RALLY, false, 0));
        assertEquals(SquadManager.SquadAction.RALLY, SquadManager.chooseSquadAction(false, false, 4, alone,
                SquadStatus.RALLY, false, 0));
    }

    @Test
    void onlyARallyingSquadHasItsMoveOutRuleSuspended() {
        assertEquals(SquadManager.AIR_MOVE_OUT_UNITS,
                AirReinforcement.launchThreshold(SquadManager.AIR_MOVE_OUT_UNITS, SquadStatus.RETREAT, true));
        assertEquals(SquadManager.AIR_MOVE_OUT_UNITS,
                AirReinforcement.launchThreshold(SquadManager.AIR_MOVE_OUT_UNITS, SquadStatus.FIGHT, true));
    }

    @Test
    void fightRetreatAndHarassAreActive() {
        assertTrue(AirReinforcement.isActive(SquadStatus.FIGHT));
        assertTrue(AirReinforcement.isActive(SquadStatus.RETREAT));
        assertTrue(AirReinforcement.isActive(SquadStatus.HARASS));
        assertFalse(AirReinforcement.isActive(SquadStatus.RALLY));
        assertFalse(AirReinforcement.isActive(SquadStatus.CONTAIN));
        assertFalse(AirReinforcement.isActive(SquadStatus.RUNBY));
    }

    @Test
    void aSquadArrivesInsideTheArrivalDistance() {
        assertTrue(AirReinforcement.arrived(AirReinforcement.ARRIVAL_DISTANCE - 1));
        assertFalse(AirReinforcement.arrived(AirReinforcement.ARRIVAL_DISTANCE));
    }

    @Test
    void aReinforcingSquadSteersPastTheWaypointsItHasReached() {
        Position first = new Position(1500, 1500);
        List<Position> path = Arrays.asList(first, GOAL);

        assertSame(first, AirReinforcement.steer(FROM, path));
        assertSame(GOAL, AirReinforcement.steer(new Position(1500 + AirReinforcement.WAYPOINT_REACHED, 1500), path));
        assertSame(GOAL, AirReinforcement.steer(GOAL, Collections.singletonList(GOAL)));
    }

    @Test
    void aNewUnitJoinsAReinforcingSquadOnlyBesideIt() {
        assertTrue(SquadManager.mayJoinAirSquadAt(SquadStatus.RALLY, 3000, false));
        assertFalse(SquadManager.mayJoinAirSquadAt(SquadStatus.RALLY, 3000, true));
        assertTrue(SquadManager.mayJoinAirSquadAt(SquadStatus.RALLY, SquadManager.AIR_JOIN_DISTANCE - 1, true));
        assertFalse(SquadManager.mayJoinAirSquadAt(SquadStatus.CONTAIN, 0, false));
    }

    @Test
    void reinforcementsHoldTheEntryFromTheirRouteUntilTheLinkHoldRunsOut() {
        assertTrue(AirReinforcement.holdsEntry(1000, 1000));
        assertTrue(AirReinforcement.holdsEntry(1000, 1000 + AirReinforcement.LINK_HOLD_FRAMES - 1));
        assertFalse(AirReinforcement.holdsEntry(1000, 1000 + AirReinforcement.LINK_HOLD_FRAMES));
        assertFalse(AirReinforcement.holdsEntry(-1, 1000));
    }

    @Test
    void aStraightPathHasNoDetour() {
        assertEquals(0, AirReinforcement.detour(FROM, Collections.singletonList(GOAL)), 1e-9);
        assertEquals(0, AirReinforcement.detour(FROM, Collections.emptyList()), 1e-9);
    }

    @Test
    void aPathAroundARememberedZoneHasADetourAndKeepsClearOfIt() {
        List<AirHarassTargeting.AirThreat> zone = Collections.singletonList(
                threat(1, UnitType.Terran_Missile_Turret, MIDWAY));
        assertFalse(AirHarassTargeting.segmentClear(FROM, GOAL, zone));

        List<Position> path = AirReinforcement.safePath(FROM, GOAL, zone, ANYWHERE);

        assertNotNull(path);
        assertTrue(AirReinforcement.detour(FROM, path) > 0);
        assertEveryLegOutside(FROM, path, zone);
    }

    @Test
    void zoneMembersPricedWithTheKnownThreatsAreBothDetoured() {
        List<AirHarassTargeting.AirThreat> priced = new ArrayList<>();
        priced.add(threat(1, UnitType.Terran_Missile_Turret, new Position(1700, 2000)));
        priced.add(threat(2, UnitType.Terran_Goliath, new Position(2300, 2000)));

        List<Position> path = AirReinforcement.safePath(FROM, GOAL, priced, ANYWHERE);

        assertNotNull(path);
        assertEveryLegOutside(FROM, path, priced);
    }
}
