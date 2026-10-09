package unit.squad;

import bwapi.Position;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import bwapi.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirSnipeTest {

    private static final WeaponType GLAVE = UnitType.Zerg_Mutalisk.groundWeapon();
    private static final int DAMAGE = GLAVE.damageAmount();
    private static final int FACTOR = GLAVE.damageFactor();
    private static final Position AT = new Position(1000, 1000);

    private static AirSnipe.Candidate candidate(int id, UnitType type, int hitPoints, int armor) {
        return new AirSnipe.Candidate(id, type, AT, hitPoints, AirSnipe.perHit(DAMAGE, armor, GLAVE, type.size()));
    }

    private static List<Integer> ids(List<AirSnipe.Assignment> plan) {
        List<Integer> ids = new ArrayList<>();
        for (AirSnipe.Assignment assignment : plan) {
            ids.add(assignment.getTarget().getId());
        }
        return ids;
    }

    @Test
    void aHitDealsTheWeaponsDamageLessArmorAndNeverLessThanOne() {
        assertEquals(DAMAGE, AirSnipe.perHit(DAMAGE, 0, GLAVE, UnitSizeType.Small));
        assertEquals(DAMAGE - 3, AirSnipe.perHit(DAMAGE, 3, GLAVE, UnitSizeType.Large));
        assertEquals(1, AirSnipe.perHit(DAMAGE, DAMAGE + 10, GLAVE, UnitSizeType.Medium));
    }

    @Test
    void upgradedDamageRaisesTheHit() {
        assertEquals(DAMAGE + 2, AirSnipe.perHit(DAMAGE + 2, 0, GLAVE, UnitSizeType.Small));
    }

    @Test
    void alphaIsTheMutalisksTimesTheHitsPerAttackTimesTheHit() {
        assertEquals(8 * FACTOR * DAMAGE, AirSnipe.alpha(8, FACTOR, DAMAGE));
        assertEquals(0, AirSnipe.alpha(0, FACTOR, DAMAGE));
        assertEquals(0, AirSnipe.alpha(-1, FACTOR, DAMAGE));
    }

    @Test
    void aTargetIsASnipeWhenItsPoolDoesNotExceedTheAlpha() {
        assertTrue(AirSnipe.isSnipe(72, 72));
        assertTrue(AirSnipe.isSnipe(40, 72));
        assertFalse(AirSnipe.isSnipe(73, 72));
    }

    @Test
    void aWorkerOnlyAVolleyCoversIsLeftOutOfThePlan() {
        AirSnipe.Candidate scv = candidate(1, UnitType.Terran_SCV, UnitType.Terran_SCV.maxHitPoints(), 0);
        int needed = (scv.getHitPoints() + DAMAGE * FACTOR - 1) / (DAMAGE * FACTOR);

        assertTrue(AirSnipe.plan(Collections.singletonList(scv), needed - 1, FACTOR).isEmpty());
        List<AirSnipe.Assignment> plan = AirSnipe.plan(Collections.singletonList(scv), needed, FACTOR);
        assertEquals(1, plan.size());
        assertEquals(needed, plan.get(0).getMutas());
    }

    @Test
    void theVolleyKillsAsManyTargetsAsItCanFewestMutalisksFirst() {
        AirSnipe.Candidate scv = candidate(1, UnitType.Terran_SCV, 60, 0);
        AirSnipe.Candidate probe = candidate(2, UnitType.Protoss_Probe, 40, 0);
        AirSnipe.Candidate marine = candidate(3, UnitType.Terran_Marine, 40, 0);

        List<AirSnipe.Assignment> plan = AirSnipe.plan(Arrays.asList(scv, probe, marine), 10, FACTOR);

        assertEquals(Arrays.asList(2, 3), ids(plan));
    }

    @Test
    void equalMutalisksNeededAreRankedByKillValue() {
        AirSnipe.Candidate marine = candidate(1, UnitType.Terran_Marine, 40, 0);
        AirSnipe.Candidate firebat = candidate(2, UnitType.Terran_Firebat, 40, 0);

        List<AirSnipe.Assignment> plan = AirSnipe.plan(Arrays.asList(marine, firebat), 5, FACTOR);

        assertEquals(Collections.singletonList(2), ids(plan));
    }

    @Test
    void aTargetTheFlockCannotKillInOneVolleyIsNeverPlanned() {
        AirSnipe.Candidate tank = candidate(1, UnitType.Terran_Siege_Tank_Tank_Mode, 150, 1);

        assertTrue(AirSnipe.plan(Collections.singletonList(tank), 4, FACTOR).isEmpty());
    }

    @Test
    void anInjuredTargetNeedsFewerMutalisks() {
        AirSnipe.Candidate healthy = candidate(1, UnitType.Terran_Marine, 40, 0);
        AirSnipe.Candidate injured = candidate(2, UnitType.Terran_Marine, 9, 0);

        List<AirSnipe.Assignment> plan = AirSnipe.plan(Arrays.asList(healthy, injured), 6, FACTOR);

        assertEquals(Arrays.asList(2, 1), ids(plan));
        assertEquals(1, plan.get(0).getMutas());
    }

    @Test
    void eachPlannedTargetTakesTheMutalisksNearestIt() {
        Position near = new Position(1100, 1000);
        AirSnipe.Candidate target = new AirSnipe.Candidate(7, UnitType.Terran_Marine, near, 40,
                AirSnipe.perHit(DAMAGE, 0, GLAVE, UnitSizeType.Small));
        Map<Integer, Position> mutas = new HashMap<>();
        mutas.put(1, new Position(1090, 1000));
        mutas.put(2, new Position(1050, 1000));
        mutas.put(3, new Position(1070, 1000));
        mutas.put(4, new Position(1085, 1000));
        mutas.put(5, new Position(1095, 1000));
        mutas.put(6, new Position(3000, 1000));
        List<AirSnipe.Assignment> plan = AirSnipe.plan(Collections.singletonList(target), 6, FACTOR);

        Map<Integer, Integer> assigned = AirSnipe.assign(plan, mutas, Collections.emptyMap());

        assertEquals(5, assigned.size());
        assertFalse(assigned.containsKey(6));
        for (int target7 : assigned.values()) {
            assertEquals(7, target7);
        }
    }

    @Test
    void aMutalisksTakenByAnEarlierTargetIsNotGivenToALaterOne() {
        AirSnipe.Candidate first = new AirSnipe.Candidate(1, UnitType.Terran_Marine, new Position(1000, 1000), 18,
                AirSnipe.perHit(DAMAGE, 0, GLAVE, UnitSizeType.Small));
        AirSnipe.Candidate second = new AirSnipe.Candidate(2, UnitType.Terran_Marine, new Position(1000, 1010), 18,
                AirSnipe.perHit(DAMAGE, 0, GLAVE, UnitSizeType.Small));
        Map<Integer, Position> mutas = new HashMap<>();
        mutas.put(1, new Position(1000, 1000));
        mutas.put(2, new Position(1000, 1005));
        mutas.put(3, new Position(1000, 1010));
        mutas.put(4, new Position(1000, 1015));

        Map<Integer, Integer> assigned = AirSnipe.assign(AirSnipe.plan(Arrays.asList(first, second), 4, FACTOR),
                mutas, Collections.emptyMap());

        assertEquals(4, assigned.size());
        assertEquals(2, new java.util.HashSet<>(assigned.values()).size());
    }

    @Test
    void aMutalisksKeepsItsTargetWhileThePlanStillNeedsItEvenWhenAnotherIsNowNearer() {
        AirSnipe.Candidate first = new AirSnipe.Candidate(1, UnitType.Terran_Marine, new Position(1000, 1000), 18,
                AirSnipe.perHit(DAMAGE, 0, GLAVE, UnitSizeType.Small));
        AirSnipe.Candidate second = new AirSnipe.Candidate(2, UnitType.Terran_Marine, new Position(1000, 1010), 18,
                AirSnipe.perHit(DAMAGE, 0, GLAVE, UnitSizeType.Small));
        Map<Integer, Position> mutas = new HashMap<>();
        mutas.put(1, new Position(1000, 1000));
        mutas.put(2, new Position(1000, 1004));
        mutas.put(3, new Position(1000, 1006));
        mutas.put(4, new Position(1000, 1010));
        List<AirSnipe.Assignment> plan = AirSnipe.plan(Arrays.asList(first, second), 4, FACTOR);
        Map<Integer, Integer> before = AirSnipe.assign(plan, mutas, Collections.emptyMap());

        Map<Integer, Position> jostled = new HashMap<>();
        jostled.put(1, new Position(1000, 1005));
        jostled.put(2, new Position(1000, 1001));
        jostled.put(3, new Position(1000, 1009));
        jostled.put(4, new Position(1000, 1007));

        assertEquals(before, AirSnipe.assign(plan, jostled, before));
    }
}
