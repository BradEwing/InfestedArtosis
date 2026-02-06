package info;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;

import java.util.List;

/**
 * Manages bases information.
 *
 * Sub-manager of InformationManager.
 */
public class BaseManager {

    private BWEM bwem;
    private Game game;
    private GameState gameState;

    public BaseManager(BWEM bwem, Game game, GameState gameState) {
        this.bwem = bwem;
        this.game = game;
        this.gameState = gameState;

        init();
    }

    private void init() {
        Unit initialHatch = null;

        for (Unit unit: game.getAllUnits()) {
            // Don't count opponent hatch in ZvZ
            if (game.self() != unit.getPlayer()) {
                continue;
            }

            if (unit.getType() == UnitType.Zerg_Hatchery) {
                initialHatch = unit;
            }
        }

        List<Base> allBases = bwem.getMap().getBases();
        Base mainBase = closestBaseToUnit(initialHatch, allBases);
        gameState.addMainBase(initialHatch, mainBase);
    }

    public void onUnitDestroy(Unit unit) {
        UnitType type = unit.getType();
        boolean isHatch = type == UnitType.Zerg_Hatchery || type == UnitType.Zerg_Lair || type == UnitType.Zerg_Hive;
        if (isHatch) {
            gameState.removeHatchery(unit);
        }
    }

    private Base closestBaseToUnit(Unit unit, List<Base> baseList) {
        Base closestBase = null;
        int closestDistance = Integer.MAX_VALUE;
        for (Base b : baseList) {
            int distance = unit.getDistance(b.getLocation().toPosition());
            if (distance < closestDistance) {
                closestBase = b;
                closestDistance = distance;
            }
        }

        return closestBase;
    }
}
