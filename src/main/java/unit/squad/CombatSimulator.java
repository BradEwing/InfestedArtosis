package unit.squad;

import info.GameState;

/**
 * CombatSimulator interface for combat simulation and engagement evaluation.
 */
public interface CombatSimulator {

    CombatResult evaluate(Squad squad, GameState gameState);

    enum CombatResult {
        ENGAGE,
        RETREAT,
        REGROUP
    }
}