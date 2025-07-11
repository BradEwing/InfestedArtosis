package unit.squad;

import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import org.bk.ass.sim.BWMirrorAgentFactory;
import org.bk.ass.sim.Simulator;
import unit.managed.ManagedUnit;

import java.util.Set;

import static util.Filter.isHostileBuilding;

/**
 * Combat simulator implementation using ASS (Automated Starcraft Simulator).
 * Used for general unit combat evaluation.
 */
public class AssCombatSimulator implements CombatSimulator {

    private final BWMirrorAgentFactory agentFactory;

    public AssCombatSimulator() {
        this.agentFactory = new BWMirrorAgentFactory();
    }

    @Override
    public CombatResult evaluate(Squad squad, GameState gameState) {
        Set<Unit> enemyUnits = gameState.getVisibleEnemyUnits();
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();

        Simulator simulator = new Simulator.Builder().build();

        // Add squad units to simulation
        for (ManagedUnit managedUnit : squad.getMembers()) {
            simulator.addAgentA(agentFactory.of(managedUnit.getUnit()));
        }

        // Add nearby enemy units
        for (Unit enemyUnit : enemyUnits) {
            if (enemyUnit.getType() == UnitType.Unknown) {
                continue;
            }
            if ((int) enemyUnit.getPosition().getDistance(squad.getCenter()) > 256) {
                continue;
            }
            if (enemyUnit.isBeingConstructed() || enemyUnit.isMorphing()) {
                continue;
            }
            if (enemyUnit.getType().isWorker()) {
                continue;
            }
            try {
                simulator.addAgentB(agentFactory.of(enemyUnit));
            } catch (ArithmeticException e) {
                return CombatResult.RETREAT;
            }
        }

        // Add nearby hostile buildings
        for (Unit enemyBuilding : enemyBuildings) {
            if (!isHostileBuilding(enemyBuilding.getType())) {
                continue;
            }
            if ((int) enemyBuilding.getPosition().getDistance(squad.getCenter()) > 512) {
                continue;
            }
            if (enemyBuilding.isMorphing() || enemyBuilding.isBeingConstructed()) {
                continue;
            }
            try {
                simulator.addAgentB(agentFactory.of(enemyBuilding));
            } catch (ArithmeticException e) {
                return CombatResult.RETREAT;
            }
        }

        // Run simulation (15 seconds)
        simulator.simulate(150);

        // Evaluate results
        if (simulator.getAgentsA().isEmpty()) {
            return CombatResult.RETREAT;
        }

        if (!simulator.getAgentsB().isEmpty()) {
            // If less than 40% of units survive, retreat
            float percentRemaining = (float) simulator.getAgentsA().size() / squad.getMembers().size();
            if (percentRemaining < 0.40) {
                return CombatResult.RETREAT;
            }
        }

        return CombatResult.ENGAGE;
    }
}