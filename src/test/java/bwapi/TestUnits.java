package bwapi;

import com.sun.jna.Memory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds real JBWAPI units over an in-memory client data buffer, for tests that must run a unit's own accessors
 * instead of a copy of the logic behind them. Latency compensation is off, so every accessor reads the buffer the
 * way it reads the BWAPI server's shared memory in a game. The game's unit table holds every unit built, so an
 * accessor that returns another unit, such as a target, resolves it by id. A new unit targets nothing.
 */
public final class TestUnits {

    private static final int MAX_UNITS = 64;

    private final Memory memory = new Memory((long) MAX_UNITS * ClientData.UnitData.SIZE);
    private final ClientData clientData = new ClientData();
    private final Game game = new Game();
    private final Map<Unit, ClientData.UnitData> data = new HashMap<>();
    private final Unit[] units = new Unit[MAX_UNITS];

    public TestUnits() {
        memory.clear();
        clientData.setBuffer(new WrappedBuffer(memory, MAX_UNITS * ClientData.UnitData.SIZE));
        try {
            Field latcom = Game.class.getDeclaredField("latcom");
            latcom.setAccessible(true);
            latcom.setBoolean(game, false);
            Field unitTable = Game.class.getDeclaredField("units");
            unitTable.setAccessible(true);
            unitTable.set(game, units);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return the game the units are built against
     */
    public Game game() {
        return game;
    }

    /**
     * Builds a unit of the given type whose id is the next free index.
     */
    public Unit unit(UnitType type) {
        return unit(type, data.size());
    }

    /**
     * Builds a unit of the given type with the given id, in the next free slot of the buffer.
     */
    public Unit unit(UnitType type, int id) {
        int index = data.size();
        ClientData.UnitData unitData = clientData.new UnitData(index * ClientData.UnitData.SIZE);
        unitData.setId(id);
        unitData.setType(type.id);
        unitData.setTarget(-1);
        unitData.setOrderTarget(-1);
        Unit unit = new Unit(unitData, id, game);
        data.put(unit, unitData);
        units[id] = unit;
        return unit;
    }

    /**
     * Sets whether the unit reports that it is starting an attack this frame, as the BWAPI server writes it.
     */
    public void setStartingAttack(Unit unit, boolean startingAttack) {
        data.get(unit).setIsStartingAttack(startingAttack);
    }

    /**
     * Sets the unit's ground and air weapon cooldowns, as the BWAPI server writes them.
     */
    public void setWeaponCooldowns(Unit unit, int ground, int air) {
        data.get(unit).setGroundWeaponCooldown(ground);
        data.get(unit).setAirWeaponCooldown(air);
    }

    /**
     * Sets the unit's weapon target and its order target, null for none, as the BWAPI server writes them.
     */
    public void setTargets(Unit unit, Unit target, Unit orderTarget) {
        data.get(unit).setTarget(target == null ? -1 : target.getID());
        data.get(unit).setOrderTarget(orderTarget == null ? -1 : orderTarget.getID());
    }

    /**
     * Sets whether the unit exists, as the BWAPI server writes it.
     */
    public void setExists(Unit unit, boolean exists) {
        data.get(unit).setExists(exists);
    }
}
