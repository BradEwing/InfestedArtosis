package telemetry;

import bwapi.Position;
import bwapi.UnitType;
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
    private DecisionPath decisionPath = DecisionPath.NONE;

    private double ourStrength = NOT_EVALUATED;
    private double enemyStrength = NOT_EVALUATED;
    private double ratio = NOT_EVALUATED;
    private double engageThreshold = NOT_EVALUATED;
    private int enemySupplyBelieved = NOT_EVALUATED;
    private String enemyComposition = "NONE";
    private int enemyUnscoredSupply = NOT_EVALUATED;

    private RallyRelease rallyRelease = RallyRelease.NONE;

    private int shouldContain = NOT_EVALUATED;
    private int canBreakContainment = NOT_EVALUATED;
    private int containmentEntered = NOT_EVALUATED;

    private Position pushbackFrom;
    private Position pushbackTo;
    private UnitType pushbackEnemyType;
    private int pushbackMembersMoved = NOT_EVALUATED;
    private int containSupplyLost = NOT_EVALUATED;
    private int outrangedHit = NOT_EVALUATED;
    private int moveOutThreshold = NOT_EVALUATED;
    private int moveOutStrength = NOT_EVALUATED;

    static int tristate(boolean value) {
        return value ? 1 : 0;
    }
}
