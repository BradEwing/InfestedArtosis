package telemetry;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import unit.managed.ManagedUnit;
import util.TargetScorer;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every fight target change TargetScorer makes for a squad fighter: the attacker, the target
 * it chose, the tier that target was assigned, and the target it replaced.
 *
 * <p>Only changes are written. A row stays in force until the next row for the same attacker_id, so
 * time spent on a target is the frame gap to that next row, or to the attacker's death.
 *
 * <p>previous_target_id is -1 and previous_target_type is NONE when the attacker held no target.
 *
 * <p>scout_capped is 1 when the scout chase cap removed a scout from the attacker's candidates, so
 * candidate_count excludes it.
 *
 * <p>squad_id is the fight squad whose targeting pass made the choice, empty for choices made outside one.
 * assigned_count is how many other melee attackers, from any fight squad, held the target in the frame's shared
 * TargetLedger when this attacker chose it. saturated is 0 when the attacker attacks the target with a slot open,
 * 1 when that count had reached the melee cap for this attacker and it still attacks the target directly, and 2 when
 * the attacker is in overflow (see util.MeleeOverflowGate): it attack-moves past the target, leaving what it
 * hits to the game, and is not held in the ledger. A row is also written when the attacker keeps its target but enters
 * or leaves overflow, so previous_target_id then equals target_id; each change of the cell between 2 and 0 or 1 for
 * one attacker_id is one gate transition. priority_reason is why the target got its tier, see TargetScorer.Reason.
 * widened is 1 when every target within the targeting radius was saturated and the target was found among candidates
 * out to twice that radius; candidate_count then counts the widened candidates.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class TargetChoiceLogger implements TargetChoiceSink {

    static final String FILE = "telemetry_target_choices.csv";

    static final String HEADER = "game_id,frame,attacker_id,attacker_type,target_id,target_type,tier,distance_px,"
            + "candidate_count,previous_target_id,previous_target_type,scout_capped,squad_id,assigned_count,"
            + "saturated,priority_reason,widened";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NO_TARGET = -1;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;

    private boolean disabled;

    public TargetChoiceLogger(Game game, String gameId) {
        this.game = game;
        this.gameId = gameId;
        this.writer = new TelemetryWriter(FILE, HEADER);
    }

    public void onFrame() {
        if (disabled) {
            return;
        }

        try {
            if (game.getFrameCount() % FLUSH_INTERVAL_FRAMES == 0) {
                writer.flush();
            }
        } catch (RuntimeException e) {
            disable();
        }
    }

    public void onEnd() {
        if (disabled) {
            return;
        }

        try {
            writer.flush();
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onTargetChosen(ManagedUnit attacker, Unit previousTarget, TargetScorer.Selection selection,
                               boolean scoutCapped) {
        if (disabled) {
            return;
        }

        try {
            writer.append(row(attacker, previousTarget, selection, scoutCapped));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        TargetChoices.clear();
    }

    private String row(ManagedUnit attacker, Unit previousTarget, TargetScorer.Selection selection,
                       boolean scoutCapped) {
        Unit target = selection.getTarget();
        List<String> fields = new ArrayList<>(attackerCells(gameId, game.getFrameCount(), attacker.getUnitID(),
                attacker.getUnitType()));
        fields.addAll(targetCells(target.getID(), target.getType(), selection.getPriority(),
                attacker.getUnit().getDistance(target), selection.getCandidateCount()));
        fields.addAll(previousTargetCells(previousTarget != null ? previousTarget.getID() : NO_TARGET,
                previousTarget != null ? previousTarget.getType() : null));
        fields.add(scoutCappedCell(scoutCapped));
        fields.addAll(loadCells(selection.getSquadId(), selection.getAssignedCount(), selection.isSaturated(),
                selection.isAttackMove(), selection.getReason(), selection.isWidened()));
        return String.join(",", fields);
    }

    /**
     * Builds the cells from squad_id through widened.
     */
    static List<String> loadCells(String squadId, int assignedCount, boolean saturated, boolean attackMove,
                                  TargetScorer.Reason reason, boolean widened) {
        List<String> fields = new ArrayList<>();
        fields.add(Csv.sanitize(squadId));
        fields.add(String.valueOf(assignedCount));
        fields.add(saturatedCell(saturated, attackMove));
        fields.add(Csv.name(reason));
        fields.add(widened ? "1" : "0");
        return fields;
    }

    /**
     * Builds the saturated cell: 2 for an overflow attack-move, otherwise 1 for a saturated pick and 0 for an open one.
     */
    static String saturatedCell(boolean saturated, boolean attackMove) {
        if (attackMove) {
            return "2";
        }
        return saturated ? "1" : "0";
    }

    /**
     * Builds the cells from game_id through attacker_type.
     */
    static List<String> attackerCells(String gameId, int frame, int attackerId, UnitType attackerType) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(frame));
        fields.add(String.valueOf(attackerId));
        fields.add(Csv.name(attackerType));
        return fields;
    }

    /**
     * Builds the cells from target_id through candidate_count.
     */
    static List<String> targetCells(int targetId, UnitType targetType, TargetScorer.Priority tier, int distance,
                                    int candidateCount) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(targetId));
        fields.add(Csv.name(targetType));
        fields.add(Csv.name(tier));
        fields.add(String.valueOf(distance));
        fields.add(String.valueOf(candidateCount));
        return fields;
    }

    /**
     * Builds the scout_capped cell.
     */
    static String scoutCappedCell(boolean scoutCapped) {
        return scoutCapped ? "1" : "0";
    }

    /**
     * Builds the previous_target_id and previous_target_type cells.
     */
    static List<String> previousTargetCells(int previousTargetId, UnitType previousTargetType) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(previousTargetId));
        fields.add(Csv.name(previousTargetType));
        return fields;
    }
}
