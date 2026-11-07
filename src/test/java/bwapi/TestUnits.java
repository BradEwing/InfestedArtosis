package bwapi;

import com.sun.jna.Memory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds real JBWAPI units over an in-memory client data buffer, for tests that must run a unit's own accessors
 * instead of a copy of the logic behind them. Latency compensation is off, so every accessor reads the buffer the
 * way it reads the BWAPI server's shared memory in a game. The game's unit table holds every unit built, so an
 * accessor that returns another unit, such as a target, resolves it by id. A new unit targets nothing.
 */
public final class TestUnits {

    private static final int MAX_UNITS = 64;
    private static final int MAX_PLAYERS = 12;
    /**
     * Bytes of the shared game data a test game is backed with: up to and including the frame count, which JBWAPI
     * 2.2.0 reads at offset 3446096 of the game data.
     */
    private static final int GAME_DATA_BYTES = 3446096 + Integer.BYTES;

    private final Memory memory = new Memory((long) MAX_UNITS * ClientData.UnitData.SIZE);
    private final ClientData clientData = new ClientData();
    private final Memory playerMemory = new Memory((long) MAX_PLAYERS * ClientData.PlayerData.SIZE);
    private final ClientData playerClientData = new ClientData();
    private final Game game = new Game();
    private final Map<Unit, ClientData.UnitData> data = new HashMap<>();
    private final Unit[] units = new Unit[MAX_UNITS];
    private final Player[] players = new Player[MAX_PLAYERS];
    private final List<Unit> built = new ArrayList<>();
    private Memory gameMemory;
    private int positionFrame = 0;

    public TestUnits() {
        memory.clear();
        playerMemory.clear();
        playerClientData.setBuffer(new WrappedBuffer(playerMemory, MAX_PLAYERS * ClientData.PlayerData.SIZE));
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
        built.add(unit);
        return unit;
    }

    /**
     * Sets whether the unit reports that it is starting an attack this frame, as the BWAPI server writes it.
     */
    public void setStartingAttack(Unit unit, boolean startingAttack) {
        data.get(unit).setIsStartingAttack(startingAttack);
    }

    /**
     * Sets the unit's order and the position it targets, as the BWAPI server writes them.
     */
    public void setOrder(Unit unit, Order order, Position target) {
        data.get(unit).setOrder(order.id);
        data.get(unit).setOrderTargetPositionX(target.getX());
        data.get(unit).setOrderTargetPositionY(target.getY());
    }

    /**
     * Sets whether the unit reports that it is playing an attack frame, as the BWAPI server writes it.
     */
    public void setAttackFrame(Unit unit, boolean attackFrame) {
        data.get(unit).setIsAttackFrame(attackFrame);
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

    /**
     * Sets the frame the game reports. The first call backs the game with a buffer large enough to hold the frame
     * count, which the game reads out of its shared game data.
     */
    public void setFrame(int frame) {
        if (gameMemory == null) {
            gameMemory = new Memory(GAME_DATA_BYTES);
            gameMemory.clear();
            try {
                Field field = Game.class.getDeclaredField("clientData");
                field.setAccessible(true);
                ((ClientData) field.get(game)).setBuffer(new WrappedBuffer(gameMemory, GAME_DATA_BYTES));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        game.botClientData().gameData().setFrameCount(frame);
    }

    /**
     * Builds the player with the given id, with no upgrades or research, and makes it the game's own player when
     * self holds. Every unit built afterwards is listed among the game's units.
     */
    public Player player(int id, boolean self) {
        ClientData.PlayerData playerData = playerClientData.new PlayerData(id * ClientData.PlayerData.SIZE);
        Player player = new Player(playerData, id, game);
        players[id] = player;
        try {
            Field playerTable = Game.class.getDeclaredField("players");
            playerTable.setAccessible(true);
            playerTable.set(game, players);
            Field allUnits = Game.class.getDeclaredField("allUnits");
            allUnits.setAccessible(true);
            allUnits.set(game, built);
            if (self) {
                Field selfField = Game.class.getDeclaredField("self");
                selfField.setAccessible(true);
                selfField.set(game, player);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return player;
    }

    /**
     * Gives the unit to a player, as the BWAPI server writes it.
     */
    public void setPlayer(Unit unit, Player player) {
        data.get(unit).setPlayer(player.getID());
    }

    /**
     * Sets whether the given player sees the unit, as the BWAPI server writes it.
     */
    public void setVisible(Unit unit, Player player, boolean visible) {
        data.get(unit).setIsVisible(player.getID(), visible);
    }

    /**
     * Sets the unit's hit points, as the BWAPI server writes them.
     */
    public void setHitPoints(Unit unit, int hitPoints) {
        data.get(unit).setHitPoints(hitPoints);
    }

    /**
     * Sets whether the unit is completed, as the BWAPI server writes it.
     */
    public void setCompleted(Unit unit, boolean completed) {
        data.get(unit).setIsCompleted(completed);
    }

    /**
     * Places the unit at a pixel position, as the BWAPI server writes it and the game's frame update reads it.
     */
    public void setPosition(Unit unit, int x, int y) {
        data.get(unit).setPositionX(x);
        data.get(unit).setPositionY(y);
        unit.updatePosition(++positionFrame);
    }
}
