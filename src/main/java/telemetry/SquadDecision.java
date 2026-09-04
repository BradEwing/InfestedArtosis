package telemetry;

import lombok.Getter;
import lombok.Setter;
import unit.squad.CombatSimulator;

/**
 * Everything SquadManager computed about one squad on one frame, held until the frame's status
 * sweep turns it into a row.
 *
 * <p>Fields left at {@link #NOT_EVALUATED} mean the decision path never reached that computation,
 * which is a different fact from a false verdict and is kept distinguishable in the CSV.
 */
@Getter
@Setter
final class SquadDecision {

    static final int NOT_EVALUATED = -1;

    private CombatSimulator.CombatResult result;
    private boolean simSampled;
    private boolean retreatLocked;
    private boolean fightLocked;

    private double ourStrength = NOT_EVALUATED;
    private double enemyStrength = NOT_EVALUATED;
    private double enemyStrengthInner = NOT_EVALUATED;
    private double enemyStrengthMiddle = NOT_EVALUATED;
    private double enemyStrengthOuter = NOT_EVALUATED;
    private double excludedEnemyStrengthInner = NOT_EVALUATED;
    private double excludedEnemyStrengthMiddle = NOT_EVALUATED;
    private double excludedEnemyStrengthOuter = NOT_EVALUATED;
    private double excludedEnemyStrengthBeyond = NOT_EVALUATED;
    private double ratio = NOT_EVALUATED;
    private double engageThreshold = NOT_EVALUATED;
    private int enemySupplyBelieved = NOT_EVALUATED;

    private int shouldContain = NOT_EVALUATED;
    private int canBreakContainment = NOT_EVALUATED;
    private int containmentEntered = NOT_EVALUATED;

    static int tristate(boolean value) {
        return value ? 1 : 0;
    }
}
