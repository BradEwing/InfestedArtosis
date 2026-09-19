package unit.squad;

import lombok.Value;

/**
 * Inputs and result of one simulated worker defence: agent counts on each side before and after the fight.
 *
 * <p>An unsimulated result is one the simulator could not build, and never wins.
 */
@Value
public class DefenseSim {
    boolean simulated;
    int defenders;
    int enemies;
    int defenderSurvivors;
    int enemySurvivors;
    double threshold;

    public static DefenseSim unsimulated(int defenders, double threshold) {
        return new DefenseSim(false, defenders, 0, 0, 0, threshold);
    }

    public boolean wins() {
        return simulated && WorkerDefense.defenceWins(defenders, defenderSurvivors, enemySurvivors, threshold);
    }
}
