package unit.squad;

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
    void unitsOtherThanScourgeKeepInterceptorsAsCandidates() {
        for (UnitType attacker : Arrays.asList(UnitType.Zerg_Hydralisk, UnitType.Zerg_Mutalisk,
                UnitType.Zerg_Devourer)) {
            assertFalse(InterceptorTargeting.ignoresInterceptors(attacker), attacker.toString());
            assertEquals(CARRIER_AND_INTERCEPTORS, candidatesFor(attacker, CARRIER_AND_INTERCEPTORS),
                    attacker.toString());
        }
    }
}
