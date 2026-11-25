package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptorTargetingTest {

    private static final List<UnitType> CARRIER_AND_INTERCEPTORS = Arrays.asList(
            UnitType.Protoss_Interceptor, UnitType.Protoss_Carrier, UnitType.Protoss_Interceptor,
            UnitType.Protoss_Interceptor);

    private static final List<UnitType> RANGED = Arrays.asList(UnitType.Zerg_Hydralisk, UnitType.Zerg_Mutalisk);

    private static final Position OBJECTIVE = new Position(200, 200);

    private static List<UnitType> candidatesFor(UnitType attacker, List<UnitType> visible) {
        return InterceptorTargeting.admitted(attacker, visible, Function.identity());
    }

    @Test
    void aScourgeIgnoresInterceptors() {
        assertTrue(InterceptorTargeting.ignoresInterceptors(UnitType.Zerg_Scourge));
        assertFalse(InterceptorTargeting.admits(UnitType.Zerg_Scourge, UnitType.Protoss_Interceptor));
    }

    @Test
    void aScourgeIsAdmittedEveryOtherAirUnit() {
        for (UnitType type : UnitType.values()) {
            if (type != UnitType.Protoss_Interceptor) {
                assertTrue(InterceptorTargeting.admits(UnitType.Zerg_Scourge, type), type.toString());
            }
        }
    }

    @Test
    void aScourgeSeeingOnlyInterceptorsHasNoCandidate() {
        List<UnitType> visible = Arrays.asList(UnitType.Protoss_Interceptor, UnitType.Protoss_Interceptor);

        assertTrue(candidatesFor(UnitType.Zerg_Scourge, visible).isEmpty());
    }

    @Test
    void aScourgeSeeingACarrierAndItsInterceptorsKeepsOnlyTheCarrier() {
        assertEquals(Collections.singletonList(UnitType.Protoss_Carrier),
                candidatesFor(UnitType.Zerg_Scourge, CARRIER_AND_INTERCEPTORS));
    }

    @Test
    void aScourgeKeepsItsOtherCandidatesInOrder() {
        List<UnitType> visible = Arrays.asList(UnitType.Protoss_Corsair, UnitType.Protoss_Interceptor,
                UnitType.Protoss_Carrier, UnitType.Protoss_Observer);

        assertEquals(Arrays.asList(UnitType.Protoss_Corsair, UnitType.Protoss_Carrier, UnitType.Protoss_Observer),
                candidatesFor(UnitType.Zerg_Scourge, visible));
    }

    @Test
    void unitsThatNeitherIgnoreInterceptorsNorTakeThemLastKeepThemAsCandidates() {
        for (UnitType attacker : Arrays.asList(UnitType.Zerg_Devourer, UnitType.Zerg_Guardian,
                UnitType.Zerg_Zergling)) {
            assertFalse(InterceptorTargeting.ignoresInterceptors(attacker), attacker.toString());
            assertFalse(InterceptorTargeting.takesInterceptorsLast(attacker), attacker.toString());
            assertEquals(CARRIER_AND_INTERCEPTORS, candidatesFor(attacker, CARRIER_AND_INTERCEPTORS),
                    attacker.toString());
            assertEquals(CARRIER_AND_INTERCEPTORS,
                    InterceptorTargeting.preferred(attacker, CARRIER_AND_INTERCEPTORS, Function.identity()),
                    attacker.toString());
            assertFalse(InterceptorTargeting.yields(attacker, UnitType.Protoss_Interceptor), attacker.toString());
        }
    }

    @Test
    void aHydraliskAndAMutaliskSeeingACarrierKeepNoInterceptor() {
        for (UnitType attacker : RANGED) {
            assertFalse(InterceptorTargeting.ignoresInterceptors(attacker), attacker.toString());
            assertTrue(InterceptorTargeting.takesInterceptorsLast(attacker), attacker.toString());
            assertEquals(Collections.singletonList(UnitType.Protoss_Carrier),
                    candidatesFor(attacker, CARRIER_AND_INTERCEPTORS), attacker.toString());
        }
    }

    @Test
    void aHydraliskAndAMutaliskSeeingNoCarrierKeepInterceptorsUntilTheyPick() {
        List<UnitType> visible = Arrays.asList(UnitType.Protoss_Interceptor, UnitType.Protoss_Pylon,
                UnitType.Protoss_Interceptor);

        for (UnitType attacker : RANGED) {
            assertEquals(visible, candidatesFor(attacker, visible), attacker.toString());
        }
    }

    @Test
    void aHydraliskAndAMutaliskPickFromAnythingButInterceptorsWhenAnythingElseRemains() {
        List<UnitType> nearby = Arrays.asList(UnitType.Protoss_Interceptor, UnitType.Protoss_Zealot,
                UnitType.Protoss_Interceptor, UnitType.Protoss_Corsair);

        for (UnitType attacker : RANGED) {
            assertEquals(Arrays.asList(UnitType.Protoss_Zealot, UnitType.Protoss_Corsair),
                    InterceptorTargeting.preferred(attacker, nearby, Function.identity()), attacker.toString());
        }
    }

    @Test
    void aHydraliskAndAMutaliskLeftWithInterceptorsOnlyKeepThem() {
        List<UnitType> nearby = Arrays.asList(UnitType.Protoss_Interceptor, UnitType.Protoss_Interceptor);

        for (UnitType attacker : RANGED) {
            assertEquals(nearby, InterceptorTargeting.preferred(attacker, nearby, Function.identity()),
                    attacker.toString());
        }
    }

    @Test
    void aHydraliskAndAMutaliskYieldToAnInterceptorAndToNothingElse() {
        for (UnitType attacker : RANGED) {
            assertTrue(InterceptorTargeting.yields(attacker, UnitType.Protoss_Interceptor), attacker.toString());
            for (UnitType target : UnitType.values()) {
                if (target != UnitType.Protoss_Interceptor) {
                    assertFalse(InterceptorTargeting.yields(attacker, target), target.toString());
                }
            }
        }
    }

    @Test
    void aScourgeDoesNotYieldSinceItIsNeverGivenAnInterceptor() {
        assertFalse(InterceptorTargeting.yields(UnitType.Zerg_Scourge, UnitType.Protoss_Interceptor));
        assertFalse(InterceptorTargeting.takesInterceptorsLast(UnitType.Zerg_Scourge));
    }

    @Test
    void theAttackMoveIsAimedAtTheNearestTrackedCarrier() {
        Position from = new Position(1000, 1000);
        Position near = new Position(1400, 1000);
        Position far = new Position(3000, 3000);

        assertEquals(near, InterceptorTargeting.attackMovePoint(from, Arrays.asList(far, near), () -> {
            throw new AssertionError("the objective is not read while a Carrier is tracked");
        }));
    }

    @Test
    void theAttackMoveIsAimedAtTheSquadObjectiveWhenNoCarrierIsTracked() {
        assertEquals(OBJECTIVE, InterceptorTargeting.attackMovePoint(new Position(1000, 1000),
                Collections.emptyList(), () -> OBJECTIVE));
    }

    @Test
    void aHydraliskAndAMutaliskAttackMoveInsteadOfAttackingAnInterceptor() {
        for (UnitType attacker : RANGED) {
            assertTrue(InterceptorTargeting.attackMoves(attacker, UnitType.Protoss_Interceptor, true, true),
                    attacker.toString());
        }
    }

    @Test
    void aHydraliskAndAMutaliskAttackMoveToAPickOutOfReachWhileInterceptorsAreInReach() {
        for (UnitType attacker : RANGED) {
            assertTrue(InterceptorTargeting.attackMoves(attacker, UnitType.Protoss_Carrier, false, true),
                    attacker.toString());
            assertTrue(InterceptorTargeting.attackMoves(attacker, UnitType.Protoss_Pylon, false, true),
                    attacker.toString());
        }
    }

    @Test
    void aHydraliskAndAMutaliskAttackAPickInReachOrWithNoInterceptorNear() {
        for (UnitType attacker : RANGED) {
            assertFalse(InterceptorTargeting.attackMoves(attacker, UnitType.Protoss_Carrier, true, true),
                    attacker.toString());
            assertFalse(InterceptorTargeting.attackMoves(attacker, UnitType.Protoss_Carrier, false, false),
                    attacker.toString());
            assertFalse(InterceptorTargeting.attackMoves(attacker, UnitType.Protoss_Zealot, true, false),
                    attacker.toString());
        }
    }

    @Test
    void noOtherUnitAttackMovesBecauseOfInterceptors() {
        for (UnitType attacker : Arrays.asList(UnitType.Zerg_Scourge, UnitType.Zerg_Zergling,
                UnitType.Zerg_Devourer)) {
            assertFalse(InterceptorTargeting.attackMoves(attacker, UnitType.Protoss_Interceptor, true, true),
                    attacker.toString());
            assertFalse(InterceptorTargeting.attackMoves(attacker, UnitType.Protoss_Carrier, false, true),
                    attacker.toString());
        }
    }
}
