package unit.managed;

import bwapi.Position;
import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutaliskCooldownTest {

    private static final Position RETREAT_POINT = new Position(2000, 2500);
    private static final CooldownKite.Threat SPORE = new CooldownKite.Threat(new Position(2100, 2000), 200);
    private static final CooldownKite.Threat HYDRALISK = new CooldownKite.Threat(new Position(2050, 2000), 150, true,
            UnitType.Zerg_Hydralisk.topSpeed());

    private final boolean kiteSetting = Config.mutaCooldownKite;
    private final TestUnits units = new TestUnits();

    @AfterEach
    void restoreSwitch() {
        Config.mutaCooldownKite = kiteSetting;
    }

    /**
     * A Mutalisk whose game reads and commands are replaced, so its own fight logic runs against the test buffer.
     */
    private static final class Probe extends Mutalisk {
        private final List<Position> moves = new ArrayList<>();
        private final List<Unit> attacks = new ArrayList<>();
        private List<CooldownKite.Threat> threats = Collections.emptyList();
        private int threatScans;
        private int frame;

        Probe(TestUnits units, Unit unit) {
            super(units.game(), unit, UnitRole.FIGHT, null);
        }

        @Override
        protected List<CooldownKite.Threat> airThreats() {
            threatScans++;
            return threats;
        }

        @Override
        public Position getRetreatPosition() {
            return RETREAT_POINT;
        }

        @Override
        protected int currentFrame() {
            return frame;
        }

        @Override
        protected int overheadFrames() {
            return 3;
        }

        @Override
        protected double ownTopSpeed() {
            return UnitType.Zerg_Mutalisk.topSpeed();
        }

        @Override
        protected int mapWidthPixels() {
            return 4096;
        }

        @Override
        protected int mapHeightPixels() {
            return 4096;
        }

        @Override
        protected int targetReach(UnitType targetType) {
            return 110;
        }

        @Override
        protected void setUnready(int unreadyFrames) {
        }

        @Override
        protected void issueMove(Position point) {
            moves.add(point);
        }

        @Override
        protected void issueAttack(Unit target) {
            attacks.add(target);
        }
    }

    private Unit mutaUnit;

    private Probe fightingMutalisk() {
        mutaUnit = units.unit(UnitType.Zerg_Mutalisk);
        units.setPosition(mutaUnit, 2000, 2000);
        Unit target = units.unit(UnitType.Zerg_Drone);
        units.setExists(target, true);
        units.setPosition(target, 2000, 1900);
        Probe probe = new Probe(units, mutaUnit);
        probe.fightTarget = target;
        return probe;
    }

    private void fire(Probe probe, int frame, int ground, int air) {
        units.setWeaponCooldowns(mutaUnit, 0, 0);
        probe.observeAttack(frame - 1);
        units.setWeaponCooldowns(mutaUnit, ground, air);
        probe.observeAttack(frame);
        probe.frame = frame;
    }

    @Test
    void theKiteReadsWhicheverCooldownFieldTheGameCountsDown() {
        Probe probe = fightingMutalisk();

        units.setWeaponCooldowns(mutaUnit, 17, 0);
        assertEquals(17, probe.cooldownFramesLeft());

        units.setWeaponCooldowns(mutaUnit, 0, 17);
        assertEquals(17, probe.cooldownFramesLeft());

        units.setWeaponCooldowns(mutaUnit, 0, 0);
        assertEquals(0, probe.cooldownFramesLeft());
    }

    @Test
    void aGroundCooldownPlansABackOff() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();
        probe.threats = Collections.singletonList(SPORE);

        fire(probe, 100, 17, 0);
        probe.fight();

        assertEquals(1, probe.moves.size());
        assertNotNull(probe.getKitePoint());
        assertTrue(SPORE.margin(probe.getKitePoint()) > 0);
        assertEquals(1, probe.getAttackCycles().getKiteBackoffs());
    }

    @Test
    void anAirCooldownPlansABackOff() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();
        probe.threats = Collections.singletonList(SPORE);

        fire(probe, 100, 0, 17);
        probe.fight();

        assertEquals(1, probe.moves.size());
        assertEquals(1, probe.getAttackCycles().getKiteBackoffs());
    }

    @Test
    void withNoThreatTheKiteKeepsAttacking() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();

        fire(probe, 100, 17, 0);
        probe.fight();

        assertTrue(probe.moves.isEmpty());
        assertEquals(1, probe.attacks.size());
        assertNull(probe.getKitePoint());
        assertEquals(1, probe.getAttackCycles().getKiteNoThreat());
    }

    @Test
    void fightTakesTheKiteOnlyWithTheSwitchOn() {
        Config.mutaCooldownKite = true;
        Probe kiting = fightingMutalisk();
        kiting.threats = Collections.singletonList(SPORE);
        fire(kiting, 100, 17, 0);
        kiting.fight();

        Config.mutaCooldownKite = false;
        Probe fixed = fightingMutalisk();
        fixed.threats = Collections.singletonList(SPORE);
        fire(fixed, 100, 17, 0);
        units.setAttackFrame(mutaUnit, true);
        fixed.fight();
        assertTrue(fixed.moves.isEmpty());
        assertTrue(fixed.attacks.isEmpty());
        units.setAttackFrame(mutaUnit, false);
        fixed.frame = 101;
        fixed.fight();

        assertEquals(1, kiting.moves.size());
        assertFalse(RETREAT_POINT.equals(kiting.moves.get(0)));
        assertEquals(Collections.singletonList(RETREAT_POINT), fixed.moves);
        assertEquals(0, fixed.threatScans);
        assertEquals(0, fixed.getAttackCycles().getKitePlans());
    }

    @Test
    void withTheSwitchOffTheFleeLastsThirtyFramesThenEngages() {
        Config.mutaCooldownKite = false;
        Probe probe = fightingMutalisk();
        units.setAttackFrame(mutaUnit, true);
        probe.frame = 500;
        probe.fight();
        units.setAttackFrame(mutaUnit, false);

        probe.frame = 529;
        probe.fight();
        probe.frame = 530;
        probe.fight();

        assertEquals(Collections.singletonList(RETREAT_POINT), probe.moves);
        assertEquals(Collections.singletonList(probe.fightTarget), probe.attacks);
    }

    @Test
    void theRetreatTargetIsClearedEveryCooldown() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();
        Position stale = new Position(10, 10);

        fire(probe, 100, 0, 30);
        probe.retreatTarget = stale;
        probe.fight();
        assertNull(probe.retreatTarget);

        probe.retreatTarget = stale;
        probe.frame = 105;
        probe.fight();
        assertSame(stale, probe.retreatTarget);

        fire(probe, 131, 0, 30);
        probe.fight();
        assertNull(probe.retreatTarget);
    }

    @Test
    void aRoleChangeDropsThePlan() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();
        probe.threats = Collections.singletonList(SPORE);
        fire(probe, 100, 0, 30);
        probe.fight();
        assertNotNull(probe.getKitePoint());

        probe.setRole(UnitRole.RETREAT);
        assertNull(probe.getKitePoint());
        probe.setRole(UnitRole.FIGHT);
        probe.fight();

        assertEquals(2, probe.getAttackCycles().getKitePlans());
    }

    @Test
    void aBackOffMoveIsNotOrderedAgainWhileTheUnitIsMovingThere() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();
        probe.threats = Collections.singletonList(SPORE);
        fire(probe, 100, 0, 30);
        probe.fight();
        Position point = probe.getKitePoint();
        units.setOrder(mutaUnit, bwapi.Order.Move, point);

        probe.frame = 101;
        probe.fight();

        assertEquals(1, probe.moves.size());
    }

    @Test
    void withTheSwitchOffTheReadOnlyPlanIsCountedWithoutOrders() {
        Config.mutaCooldownKite = false;
        Probe probe = fightingMutalisk();
        probe.threats = Collections.singletonList(SPORE);

        fire(probe, 100, 0, 30);
        probe.observeAttackCycle(100);
        probe.observeAttackCycle(101);

        assertEquals(1, probe.getAttackCycles().getKitePlans());
        assertEquals(1, probe.getAttackCycles().getKiteBackoffs());
        assertTrue(probe.moves.isEmpty());
        assertTrue(probe.attacks.isEmpty());
    }

    @Test
    void theThreatScanRunsOnlyWhileTheWeaponCoolsDown() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();

        units.setWeaponCooldowns(mutaUnit, 0, 0);
        probe.observeAttackCycle(10);
        assertEquals(0, probe.threatScans);

        units.setWeaponCooldowns(mutaUnit, 0, 20);
        probe.observeAttackCycle(11);
        assertEquals(1, probe.threatScans);
        assertEquals(1, probe.getAttackCycles().getCooldownFrames());
    }

    @Test
    void attackCyclesAreReadOnlyWithCombatTelemetryOn() {
        assertTrue(ManagedUnit.readsAttackCycles(true));
        assertFalse(ManagedUnit.readsAttackCycles(false));
    }

    @Test
    void theFiringFrameIsTheFrameTheCooldownRose() {
        Probe probe = fightingMutalisk();

        fire(probe, 100, 30, 0);
        units.setWeaponCooldowns(mutaUnit, 29, 0);
        probe.observeAttack(101);

        assertEquals(100, probe.getLastWeaponFireFrame());
    }

    @Test
    void withTheKiteOffAnAttackFrameOpensAThirtyFrameFleeWhateverTheCooldown() {
        int attackFrame = 500;
        Time until = new Time(attackFrame + Mutalisk.RETREAT_DURATION_FRAMES);

        assertEquals(30, Mutalisk.RETREAT_DURATION_FRAMES);
        assertEquals(Mutalisk.MainStep.START_RETREAT, Mutalisk.mainStep(true, attackFrame, null));
        assertEquals(Mutalisk.MainStep.FLEE, Mutalisk.mainStep(false, attackFrame + 1, until));
        assertEquals(Mutalisk.MainStep.FLEE, Mutalisk.mainStep(false, attackFrame + 29, until));
        assertEquals(Mutalisk.MainStep.ENGAGE, Mutalisk.mainStep(false, attackFrame + 30, until));
    }

    @Test
    void aStaticThreatComingNearAfterANoThreatPlanReplansTheCooldownIntoABackOff() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();
        fire(probe, 100, 0, 30);
        probe.fight();
        assertNull(probe.getKitePoint());

        probe.threats = Collections.singletonList(SPORE);
        probe.frame = 101;
        probe.fight();

        assertEquals(1, probe.moves.size());
        assertNotNull(probe.getKitePoint());
        assertTrue(SPORE.margin(probe.getKitePoint()) > 0);
        assertEquals(1, probe.getAttackCycles().getKiteReplans());
        assertEquals(1, probe.getAttackCycles().getKitePlans());
        assertEquals(1, probe.getAttackCycles().getKiteNoThreat());
    }

    @Test
    void withoutAStaticThreatANoThreatPlanIsNotReplanned() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();
        fire(probe, 100, 0, 30);
        probe.fight();

        probe.threats = Collections.singletonList(HYDRALISK);
        probe.frame = 101;
        probe.fight();
        probe.threats = Collections.emptyList();
        probe.frame = 102;
        probe.fight();

        assertTrue(probe.moves.isEmpty());
        assertNull(probe.getKitePoint());
        assertEquals(0, probe.getAttackCycles().getKiteReplans());
    }

    @Test
    void aReplannedCooldownIsNotReplannedAgainOrRescanned() {
        Config.mutaCooldownKite = true;
        Probe probe = fightingMutalisk();
        fire(probe, 100, 0, 30);
        probe.fight();
        probe.threats = Collections.singletonList(SPORE);
        probe.frame = 101;
        probe.fight();
        int scans = probe.threatScans;
        units.setOrder(mutaUnit, bwapi.Order.Move, probe.getKitePoint());

        for (int frame = 102; frame < 108; frame++) {
            probe.frame = frame;
            probe.fight();
        }

        assertEquals(1, probe.getAttackCycles().getKiteReplans());
        assertEquals(scans, probe.threatScans);
        assertEquals(1, probe.moves.size());
    }

    @Test
    void withTheSwitchOffAStaticThreatMidCooldownLeavesMainsOrdersAndIsReplannedReadOnly() {
        Config.mutaCooldownKite = false;
        Probe probe = fightingMutalisk();
        units.setAttackFrame(mutaUnit, true);
        probe.frame = 500;
        probe.fight();
        units.setAttackFrame(mutaUnit, false);
        fire(probe, 500, 0, 30);
        probe.observeAttackCycle(500);
        probe.threats = Collections.singletonList(SPORE);
        probe.frame = 501;
        probe.fight();
        probe.observeAttackCycle(501);
        probe.frame = 530;
        probe.fight();

        assertEquals(Collections.singletonList(RETREAT_POINT), probe.moves);
        assertEquals(Collections.singletonList(probe.fightTarget), probe.attacks);
        assertEquals(1, probe.getAttackCycles().getKitePlans());
        assertEquals(1, probe.getAttackCycles().getKiteReplans());
    }

    @Test
    void theOverheadSetsAsideLatencyTheOrderGapAndSixTurnFrames() {
        assertEquals(6, CooldownKite.TURN_FRAMES);
        assertEquals(9, Mutalisk.overheadFrames(2));
    }
}
