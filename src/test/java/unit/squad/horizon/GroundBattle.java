package unit.squad.horizon;

import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import info.tracking.DarkSwarmTracker;
import info.tracking.ObservedUnitTracker;
import unit.managed.UnitRole;
import unit.managed.Zergling;
import unit.squad.CombatSimulator.CombatResult;
import unit.squad.GroundSquad;

import java.lang.reflect.Field;
import java.util.Collections;

/**
 * A ground squad of Zerglings and the enemy around it, built from real JBWAPI units and evaluated through the
 * simulator's own entry point against a Terran opponent.
 */
final class GroundBattle {

    static final Position CENTRE = new Position(2000, 2000);
    static final int FRAME = 10000;
    private static final UnitType ZERGLING = UnitType.Zerg_Zergling;

    private final TestUnits units = new TestUnits();
    private final Player self = units.player(0, true);
    private final Player enemy = units.player(1, false);
    private final ObservedUnitTracker tracker = new ObservedUnitTracker();
    final GroundSquad squad = new GroundSquad();
    private final HorizonCombatSimulator sim = new HorizonCombatSimulator();

    GroundBattle() {
        units.setFrame(FRAME);
    }

    GroundBattle zergling(int dx, int dy) {
        Unit unit = units.unit(ZERGLING);
        units.setPlayer(unit, self);
        units.setHitPoints(unit, ZERGLING.maxHitPoints());
        units.setExists(unit, true);
        units.setCompleted(unit, true);
        units.setPosition(unit, CENTRE.getX() + dx, CENTRE.getY() + dy);
        squad.addUnit(new Zergling(units.game(), unit, UnitRole.FIGHT, null));
        return this;
    }

    GroundBattle enemy(UnitType type, int dx, int dy) {
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
