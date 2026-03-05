package unit.squad;

import info.GameState;
import unit.managed.ManagedUnit;

import java.util.Map;
import java.util.Set;

/**
 * CombatSimulator interface for combat simulation and engagement evaluation.
 */
public interface CombatSimulator {

    CombatResult evaluate(Squad squad, Set<ManagedUnit> reinforcements, GameState gameState);

    default CombatResult evaluateWithAdjacentSquads(Squad squad, Map<Squad, Double> adjacentSquads, GameState gameState) {
        return evaluate(squad, null, gameState);
    }

    enum CombatResult {
        ENGAGE,
        RETREAT,
        REGROUP
    }
}