package unit.managed;

import bwapi.Order;
import bwapi.Player;
import bwapi.Position;
import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptorAttackMoveTest {

    private static final Position CARRIER_LAST_SEEN = new Position(3000, 1000);

    private final boolean kiteSetting = Config.mutaCooldownKite;
    private final TestUnits units = new TestUnits();
    private final Player self = units.player(0, true);

    @AfterEach
    void restoreSwitch() {
        Config.mutaCooldownKite = kiteSetting;
    }

    /**
     * A Hydralisk whose attack-move command is recorded instead of sent. Its attack on a unit is not replaced, so a
     * fight that orders one fails against the test game.
     */
    private static final class HydraProbe extends Hydralisk {
        private final List<Position> attackMoves = new ArrayList<>();

        HydraProbe(TestUnits units, Unit unit) {
            super(units.game(), unit, UnitRole.FIGHT, null);
        }

        @Override
        protected void setUnready(int unreadyFrames) {
        }

        @Override
        protected void issueAttackMove(Position destination) {
            attackMoves.add(destination);
        }
    }

    /**
     * A Mutalisk whose commands are recorded instead of sent.
     */
    private static final class MutaProbe extends Mutalisk {
        private final List<Position> attackMoves = new ArrayList<>();
        private final List<Unit> attacks = new ArrayList<>();
        private final List<Position> moves = new ArrayList<>();

        MutaProbe(TestUnits units, Unit unit) {
            super(units.game(), unit, UnitRole.FIGHT, null);
        }

        @Override
        protected List<CooldownKite.Threat> airThreats() {
            return Collections.emptyList();
        }

        @Override
        protected int currentFrame() {
            return 100;
        }

        @Override
        protected void setUnready(int unreadyFrames) {
        }

        @Override
        protected void issueAttackMove(Position destination) {
            attackMoves.add(destination);
        }

        @Override
        protected void issueAttack(Unit target) {
            attacks.add(target);
        }

        @Override
        protected void issueMove(Position point) {
            moves.add(point);
        }
    }

    private Unit own(UnitType type) {
        Unit unit = units.unit(type);
        units.setExists(unit, true);
        units.setPlayer(unit, self);
        units.setPosition(unit, 2000, 2000);
        return unit;
    }

    private Unit interceptor(int x, int y) {
        Unit interceptor = units.unit(UnitType.Protoss_Interceptor);
        units.setExists(interceptor, true);
        units.setVisible(interceptor, self, true);
        units.setPosition(interceptor, x, y);
        return interceptor;
    }

    @Test
    void aHydraliskGivenAnInterceptorAsAnAttackMoveAttackMovesToThePoint() {
        HydraProbe hydra = new HydraProbe(units, own(UnitType.Zerg_Hydralisk));

        hydra.setFightTarget(interceptor(2100, 2000), true, CARRIER_LAST_SEEN);
        hydra.fight();

        assertEquals(Collections.singletonList(CARRIER_LAST_SEEN), hydra.attackMoves);
    }

    @Test
    void aHydraliskAlreadyAttackMovingToThePointIsNotOrderedAgain() {
        Unit unit = own(UnitType.Zerg_Hydralisk);
        HydraProbe hydra = new HydraProbe(units, unit);
        units.setOrder(unit, Order.AttackMove, CARRIER_LAST_SEEN);

        hydra.setFightTarget(interceptor(2100, 2000), true, CARRIER_LAST_SEEN);
        hydra.fight();

        assertTrue(hydra.attackMoves.isEmpty());
    }

    @Test
    void aHydraliskKeepsItsAttackMoveWhenTheInterceptorItWasGivenChanges() {
        Unit unit = own(UnitType.Zerg_Hydralisk);
        HydraProbe hydra = new HydraProbe(units, unit);
        hydra.setFightTarget(interceptor(2100, 2000), true, CARRIER_LAST_SEEN);
        hydra.fight();
        units.setOrder(unit, Order.AttackMove, CARRIER_LAST_SEEN);

        hydra.setFightTarget(interceptor(1900, 2000), true, CARRIER_LAST_SEEN);
        hydra.fight();

        assertEquals(1, hydra.attackMoves.size());
        assertTrue(hydra.isAttackMoving());
    }

    @Test
    void aHydraliskFollowsThePointNotTheInterceptor() {
        Unit unit = own(UnitType.Zerg_Hydralisk);
        HydraProbe hydra = new HydraProbe(units, unit);
        Unit interceptor = interceptor(2100, 2000);
        hydra.setFightTarget(interceptor, true, CARRIER_LAST_SEEN);
        hydra.fight();
        units.setOrder(unit, Order.AttackMove, CARRIER_LAST_SEEN);

        units.setPosition(interceptor, 600, 3500);
        hydra.setFightTarget(interceptor, true, CARRIER_LAST_SEEN);
        hydra.fight();

        assertEquals(Collections.singletonList(CARRIER_LAST_SEEN), hydra.attackMoves);
    }

    @Test
    void aHydraliskOnAnAttackItAcquiredOnAnEnemyOutOfRangeTakesUpItsAttackMoveAgain() {
        Unit unit = own(UnitType.Zerg_Hydralisk);
        HydraProbe hydra = new HydraProbe(units, unit);
        Unit interceptor = interceptor(2100, 2000);
        hydra.setFightTarget(interceptor, true, CARRIER_LAST_SEEN);
        hydra.fight();

        units.setPosition(interceptor, 600, 3500);
        units.setOrder(unit, Order.AttackUnit, interceptor.getPosition());
        units.setTargets(unit, interceptor, interceptor);
        hydra.setFightTarget(interceptor, true, CARRIER_LAST_SEEN);
        hydra.fight();

        assertEquals(2, hydra.attackMoves.size());
    }

    @Test
    void aMutaliskGivenAnInterceptorAsAnAttackMoveDoesNotAttackIt() {
        for (boolean kite : new boolean[] {true, false}) {
            Config.mutaCooldownKite = kite;
            MutaProbe muta = new MutaProbe(units, own(UnitType.Zerg_Mutalisk));

            muta.setFightTarget(interceptor(2100, 2000), true, CARRIER_LAST_SEEN);
            muta.fight();

            assertEquals(Collections.singletonList(CARRIER_LAST_SEEN), muta.attackMoves, "kite " + kite);
            assertTrue(muta.attacks.isEmpty(), "kite " + kite);
            assertTrue(muta.moves.isEmpty(), "kite " + kite);
        }
    }

    @Test
    void aMutaliskGivenATargetToAttackStillAttacksIt() {
        Config.mutaCooldownKite = true;
        MutaProbe muta = new MutaProbe(units, own(UnitType.Zerg_Mutalisk));
        Unit carrier = units.unit(UnitType.Protoss_Carrier);
        units.setExists(carrier, true);
        units.setVisible(carrier, self, true);
        units.setPosition(carrier, 2100, 2000);

        muta.setFightTarget(carrier);
        muta.fight();

        assertEquals(1, muta.attacks.size());
        assertSame(carrier, muta.attacks.get(0));
        assertTrue(muta.attackMoves.isEmpty());
    }

    @Test
    void aHydraliskThatReachedThePointIsNotOrderedAgain() {
        Unit unit = own(UnitType.Zerg_Hydralisk);
        HydraProbe hydra = new HydraProbe(units, unit);
        units.setOrder(unit, Order.PlayerGuard, unit.getPosition());
        units.setIdle(unit, true);

        hydra.setFightTarget(interceptor(2100, 2000), true, new Position(2010, 2010));
        hydra.fight();

        assertTrue(hydra.attackMoves.isEmpty());
    }

    @Test
    void aHydraliskNearThePointButAttackMovingElsewhereIsSentToThePoint() {
        Unit unit = own(UnitType.Zerg_Hydralisk);
        HydraProbe hydra = new HydraProbe(units, unit);
        Position near = new Position(2010, 2010);
        units.setOrder(unit, Order.AttackMove, CARRIER_LAST_SEEN);

        hydra.setFightTarget(interceptor(2100, 2000), true, near);
        hydra.fight();

        assertEquals(Collections.singletonList(near), hydra.attackMoves);
    }

    @Test
    void aHydraliskNearThePointButMovingIsSentToThePoint() {
        Unit unit = own(UnitType.Zerg_Hydralisk);
        HydraProbe hydra = new HydraProbe(units, unit);
        Position near = new Position(2010, 2010);
        units.setOrder(unit, Order.Move, CARRIER_LAST_SEEN);

        hydra.setFightTarget(interceptor(2100, 2000), true, near);
        hydra.fight();

        assertEquals(Collections.singletonList(near), hydra.attackMoves);
    }

    @Test
    void onlyAnIdleUnitAtThePointIsHeld() {
        assertTrue(ManagedUnit.holdsAtPoint(true, ManagedUnit.ATTACK_MOVE_REISSUE_DISTANCE, true));
        assertFalse(ManagedUnit.holdsAtPoint(true, ManagedUnit.ATTACK_MOVE_REISSUE_DISTANCE, false));
        assertFalse(ManagedUnit.holdsAtPoint(true, ManagedUnit.ATTACK_MOVE_REISSUE_DISTANCE + 1, true));
        assertFalse(ManagedUnit.holdsAtPoint(false, 0, true));
    }

    @Test
    void anAttackMoveToAPointCarriesOverAChangeOfTarget() {
        assertTrue(ManagedUnit.keepsAttackMove(true, false, true, true));
        assertTrue(ManagedUnit.keepsAttackMove(true, true, true, true));
    }

    @Test
    void anAttackMovePastATargetCarriesOverOnlyWhileTheTargetIsTheSame() {
        assertTrue(ManagedUnit.keepsAttackMove(true, true, false, false));
        assertFalse(ManagedUnit.keepsAttackMove(true, false, false, false));
    }

    @Test
    void anAttackMoveDoesNotCarryOverAChangeOfAimOrADirectAttack() {
        assertFalse(ManagedUnit.keepsAttackMove(true, true, false, true));
        assertFalse(ManagedUnit.keepsAttackMove(true, true, true, false));
        assertFalse(ManagedUnit.keepsAttackMove(false, true, true, true));
        assertFalse(ManagedUnit.keepsAttackMove(false, true, false, false));
    }

    @Test
    void anAcquiredAttackOnTheWayToAPointRunsOnlyWhileTheEnemyIsInRange() {
        assertTrue(ManagedUnit.keepsAcquired(true, true, true));
        assertFalse(ManagedUnit.keepsAcquired(true, true, false));
    }

    @Test
    void anAcquiredAttackOnTheWayPastATargetAlwaysRuns() {
        assertTrue(ManagedUnit.keepsAcquired(true, false, false));
        assertTrue(ManagedUnit.keepsAcquired(true, false, true));
        assertFalse(ManagedUnit.keepsAcquired(false, false, true));
        assertFalse(ManagedUnit.keepsAcquired(false, true, true));
    }
}
