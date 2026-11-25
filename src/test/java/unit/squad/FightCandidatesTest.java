package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FightCandidatesTest {

    /**
     * A visible enemy as the candidate filter reads it.
     */
    private static final class Enemy {
        private final UnitType type;
        private final boolean detected;

        Enemy(UnitType type, boolean detected) {
            this.type = type;
            this.detected = detected;
        }

        UnitType type() {
            return type;
        }

        boolean detected() {
            return detected;
        }

        boolean flying() {
            return type.isFlyer();
        }
    }

    private static final Enemy CARRIER = new Enemy(UnitType.Protoss_Carrier, true);

    private static final Enemy CORSAIR = new Enemy(UnitType.Protoss_Corsair, true);

    private static final Enemy ZEALOT = new Enemy(UnitType.Protoss_Zealot, true);

    private static Enemy interceptor() {
        return new Enemy(UnitType.Protoss_Interceptor, true);
    }

    private static List<Enemy> candidatesFor(UnitType attacker, List<Enemy> visible) {
        boolean hitsAir = attacker.airWeapon() != bwapi.WeaponType.None;
        boolean hitsGround = attacker.groundWeapon() != bwapi.WeaponType.None;
        return SquadManager.fightCandidates(attacker, visible, Enemy::type, Enemy::detected, Enemy::flying,
                enemy -> enemy.flying() ? hitsAir : hitsGround);
    }

    @Test
    void aScourgeSeeingOnlyInterceptorsGetsNoCandidate() {
        List<Enemy> visible = Arrays.asList(interceptor(), interceptor(), interceptor());

        assertTrue(candidatesFor(UnitType.Zerg_Scourge, visible).isEmpty());
    }

    @Test
    void aScourgeSeeingACarrierAndItsInterceptorsGetsOnlyTheCarrier() {
        List<Enemy> visible = Arrays.asList(interceptor(), interceptor(), CARRIER, interceptor());

        assertEquals(Collections.singletonList(CARRIER), candidatesFor(UnitType.Zerg_Scourge, visible));
    }

    @Test
    void aScourgeNeverGetsAnInterceptorAmongOtherAirUnits() {
        List<Enemy> visible = Arrays.asList(CORSAIR, interceptor(), CARRIER, ZEALOT, interceptor());

        assertEquals(Arrays.asList(CORSAIR, CARRIER), candidatesFor(UnitType.Zerg_Scourge, visible));
    }

    @Test
    void aScourgeDoesNotGetAnUndetectedAirUnit() {
        Enemy cloaked = new Enemy(UnitType.Protoss_Observer, false);

        assertEquals(Collections.singletonList(CARRIER),
                candidatesFor(UnitType.Zerg_Scourge, Arrays.asList(cloaked, CARRIER)));
    }

    @Test
    void aHydraliskAndAMutaliskKeepInterceptors() {
        List<Enemy> visible = Arrays.asList(interceptor(), CARRIER, interceptor());

        assertEquals(visible, candidatesFor(UnitType.Zerg_Hydralisk, visible));
        assertEquals(visible, candidatesFor(UnitType.Zerg_Mutalisk, visible));
    }

    @Test
    void aLurkerKeepsDetectedGroundEnemiesOnly() {
        Enemy hidden = new Enemy(UnitType.Protoss_Dark_Templar, false);
        Enemy egg = new Enemy(UnitType.Zerg_Egg, true);

        assertEquals(Arrays.asList(ZEALOT, egg),
                candidatesFor(UnitType.Zerg_Lurker, Arrays.asList(interceptor(), ZEALOT, CARRIER, hidden, egg)));
    }

    @Test
    void larvaeAndEggsAreNotCandidates() {
        List<Enemy> visible = Arrays.asList(new Enemy(UnitType.Zerg_Larva, true), new Enemy(UnitType.Zerg_Egg, true),
                ZEALOT);

        assertEquals(Collections.singletonList(ZEALOT), candidatesFor(UnitType.Zerg_Zergling, visible));
    }

    @Test
    void aScourgeSquadWithNoAirTargetIsNotHeldByAFightLock() {
        assertFalse(SquadManager.fightLockApplies(true, true));
    }

    @Test
    void aFightLockAppliesToEveryOtherSquad() {
        assertTrue(SquadManager.fightLockApplies(true, false));
        assertFalse(SquadManager.fightLockApplies(false, false));
        assertFalse(SquadManager.fightLockApplies(false, true));
    }
}
