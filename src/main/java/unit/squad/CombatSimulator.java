package unit.squad;

import info.GameState;

import java.util.Map;

/**
 * CombatSimulator interface for combat simulation and engagement evaluation.
 */
public interface CombatSimulator {

    CombatResult evaluate(Squad squad, Map<Squad, Double> adjacentSquads, GameState gameState);

    enum CombatResult {
        ENGAGE,
        RETREAT
    }
}