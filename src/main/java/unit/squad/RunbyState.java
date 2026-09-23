package unit.squad;

import bwapi.Position;
import bwem.Base;
import info.map.BaseArea;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a squad in {@link SquadStatus#RUNBY} carries between frames: the phase, the base it is raiding,
 * where it looks for workers, and the per-ling memory that keeps evasion and building targets from flapping.
 *
 * <p>PENETRATE is time boxed and only goes for workers. HARASS is open ended and adds winnable fights and
 * buildings no enemy unit covers. A retarget to another base starts PENETRATE again at the new base.
 */
@Getter
@Setter
public class RunbyState {

    /**
     * Stages of a runby.
     */
    public enum Phase {
        PENETRATE,
        HARASS
    }

    /**
     * Where the squad's seek point came from, in the order it is looked for.
     */
    public enum GoalType {
        VISIBLE,
        LAST_SEEN,
        LIKELY,
        NONE
    }

    private final int startFrame;
    private final Set<Base> visitedBases = new HashSet<>();
    private final Set<Position> visitedSpots = new HashSet<>();
    private final Map<Integer, RunbyTargeting.LingMemory> lingMemory = new HashMap<>();
    private final Map<Integer, Integer> lastHitPoints = new HashMap<>();

    private Phase phase = Phase.PENETRATE;
    private int phaseStartFrame;
    private Base targetBase;
    private BaseArea targetArea;
    private Position anchor;
    private List<Position> likelySpots = new ArrayList<>();
    private int penetrateBudgetFrames;
    private int arrivedFrame = -1;
    private boolean abortWindowClosed;
    private boolean winnable;
    private int winnableCheckedFrame = -1;
    private int lastTickFrame;
    private int lastProgressFrame;
    private GoalType goalType = GoalType.NONE;
    private Position goal;
    private double enemyTally;
    private double ourTally;
    private int workersKilled;
    private int buildingsKilled;

    public RunbyState(int startFrame) {
        this.startFrame = startFrame;
        this.phaseStartFrame = startFrame;
        this.lastTickFrame = startFrame;
        this.lastProgressFrame = startFrame;
    }

    /**
     * Points the runby at a base and starts PENETRATE toward it. The abort window is not reopened: it only
     * guards the start of the runby.
     *
     * @param base base to raid
     * @param area ground belonging to that base
     * @param anchorPoint point the entry gates and the abort tally are measured at
     * @param spots walkable points where workers are likely to be, in visiting order
     * @param budgetFrames frames PENETRATE may last at this base
     * @param frame current frame
     */
    public void target(Base base, BaseArea area, Position anchorPoint, List<Position> spots, int budgetFrames,
                       int frame) {
        this.targetBase = base;
        this.targetArea = area;
        this.anchor = anchorPoint;
        this.likelySpots = new ArrayList<>(spots);
        this.visitedSpots.clear();
        this.penetrateBudgetFrames = budgetFrames;
        this.arrivedFrame = -1;
        this.phase = Phase.PENETRATE;
        this.phaseStartFrame = frame;
        this.lastProgressFrame = frame;
        this.winnable = false;
        this.winnableCheckedFrame = -1;
        this.goal = null;
        this.goalType = GoalType.NONE;
        if (base != null) {
            visitedBases.add(base);
        }
    }

    /**
     * Moves the runby from PENETRATE to HARASS.
     *
     * @param frame current frame
     */
    public void startHarass(int frame) {
        phase = Phase.HARASS;
        phaseStartFrame = frame;
    }

    /**
     * Returns the memory kept for one ling, creating it on first use.
     *
     * @param unitId the ling's unit id
     * @return that ling's memory
     */
    public RunbyTargeting.LingMemory memoryFor(int unitId) {
        return lingMemory.computeIfAbsent(unitId, id -> new RunbyTargeting.LingMemory());
    }

    /**
     * Counts a kill credited to the runby.
     *
     * @param worker true for a worker, false for a building
     */
    public void creditKill(boolean worker) {
        if (worker) {
            workersKilled++;
        } else {
            buildingsKilled++;
        }
    }
}
