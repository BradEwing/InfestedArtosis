package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScourgeCombatSimulatorTest {

    private static final int SCOURGE_DAMAGE = UnitType.Zerg_Scourge.airWeapon().damageAmount();

    private static final int SCOURGE_PER_CARRIER =
            (int) Math.ceil((double) fullHitPoints(UnitType.Protoss_Carrier) / SCOURGE_DAMAGE);

    private static int fullHitPoints(UnitType type) {
        return type.maxHitPoints() + type.maxShields();
    }

    private static ScourgeCombatSimulator.AirTarget full(UnitType type) {
        return new ScourgeCombatSimulator.AirTarget(type, fullHitPoints(type));
    }

    private static List<ScourgeCombatSimulator.AirTarget> carrierWithInterceptors() {
        List<ScourgeCombatSimulator.AirTarget> seen = new ArrayList<>();
        seen.add(full(UnitType.Protoss_Interceptor));
        seen.add(full(UnitType.Protoss_Carrier));
        seen.add(full(UnitType.Protoss_Interceptor));
        return seen;
    }

    @Test
    void anInterceptorIsNotAnAirTarget() {
        assertFalse(ScourgeCombatSimulator.isAirTarget(UnitType.Protoss_Interceptor));
    }

    @Test
    void aCarrierAndACorsairAreAirTargets() {
        assertTrue(ScourgeCombatSimulator.isAirTarget(UnitType.Protoss_Carrier));
        assertTrue(ScourgeCombatSimulator.isAirTarget(UnitType.Protoss_Corsair));
    }

    @Test
    void aSquadSeeingOnlyInterceptorsRetreats() {
        List<ScourgeCombatSimulator.AirTarget> seen = Arrays.asList(full(UnitType.Protoss_Interceptor),
                full(UnitType.Protoss_Interceptor), full(UnitType.Protoss_Interceptor));

        assertEquals(CombatSimulator.CombatResult.RETREAT,
                ScourgeCombatSimulator.verdict(12, SCOURGE_DAMAGE, seen));
    }

    @Test
    void aSquadSeeingNothingRetreats() {
        assertEquals(CombatSimulator.CombatResult.RETREAT,
                ScourgeCombatSimulator.verdict(12, SCOURGE_DAMAGE, Collections.emptyList()));
    }

    @Test
    void anEmptySquadRetreats() {
        assertEquals(CombatSimulator.CombatResult.RETREAT,
                ScourgeCombatSimulator.verdict(0, SCOURGE_DAMAGE,
                        Collections.singletonList(full(UnitType.Protoss_Corsair))));
    }

    @Test
    void aSquadThatCanKillTheCarrierEngagesWithItsInterceptorsOut() {
        assertEquals(CombatSimulator.CombatResult.ENGAGE,
                ScourgeCombatSimulator.verdict(SCOURGE_PER_CARRIER, SCOURGE_DAMAGE, carrierWithInterceptors()));
    }

    @Test
    void interceptorsDoNotMakeASquadTooSmallForTheCarrierEngage() {
        assertEquals(CombatSimulator.CombatResult.RETREAT,
                ScourgeCombatSimulator.verdict(SCOURGE_PER_CARRIER - 1, SCOURGE_DAMAGE,
                        carrierWithInterceptors()));
    }

    @Test
    void aSquadEngagesACorsairAmongInterceptors() {
        List<ScourgeCombatSimulator.AirTarget> seen = Arrays.asList(full(UnitType.Protoss_Interceptor),
                full(UnitType.Protoss_Corsair));

        assertEquals(CombatSimulator.CombatResult.ENGAGE,
                ScourgeCombatSimulator.verdict(2, SCOURGE_DAMAGE, seen));
    }
}
