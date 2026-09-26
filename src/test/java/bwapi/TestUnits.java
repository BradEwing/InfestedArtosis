package bwapi;

import com.sun.jna.Memory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds real JBWAPI units over an in-memory client data buffer, for tests that must run a unit's own accessors
 * instead of a copy of the logic behind them. Latency compensation is off, so every accessor reads the buffer the
 * way it reads the BWAPI server's shared memory in a game.
 */
public final class TestUnits {

    private static final int MAX_UNITS = 64;

    private final Memory memory = new Memory((long) MAX_UNITS * ClientData.UnitData.SIZE);
    private final ClientData clientData = new ClientData();
    private final Game game = new Game();
    private final Map<Unit, ClientData.UnitData> data = new HashMap<>();

    public TestUnits() {
        memory.clear();
        clientData.setBuffer(new WrappedBuffer(memory, MAX_UNITS * ClientData.UnitData.SIZE));
        try {
            Field latcom = Game.class.getDeclaredField("latcom");
            latcom.setAccessible(true);
            latcom.setBoolean(game, false);
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
        int index = data.size();
        ClientData.UnitData unitData = clientData.new UnitData(index * ClientData.UnitData.SIZE);
        unitData.setId(index);
        unitData.setType(type.id);
        Unit unit = new Unit(unitData, index, game);
        data.put(unit, unitData);
        return unit;
    }

    /**
     * Sets whether the unit reports that it is starting an attack this frame, as the BWAPI server writes it.
     */
    public void setStartingAttack(Unit unit, boolean startingAttack) {
        data.get(unit).setIsStartingAttack(startingAttack);
    }
}
