package macro;

import bwapi.UnitType;
import macro.plan.Plan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the build-ahead slot: the building plan production lets reserve its cost and hold a drone
 * before the resources exist.
 */
public class BuildAheadSlot {

    static final int MIN_HOLD_FRAMES = 24 * 10;

    static final int MAX_HOLD_FRAMES = 24 * 60;

    static final int PREDICTION_GRACE_FRAMES = 24 * 15;

    static final int BACKOFF_FRAMES = 24 * 15;

    static final int REQUEUE_PENALTY_FRAMES = 24 * 15;

    static final int HOLD_REPORT_INTERVAL_FRAMES = 24 * 10;

    private static final int UNREACHABLE_FRAME = Integer.MAX_VALUE - (MAX_HOLD_FRAMES + PREDICTION_GRACE_FRAMES);

    private final Map<Plan, Claim> claims = new LinkedHashMap<>();

    private final Map<UnitType, Integer> backoffUntil = new HashMap<>();

    /** ResourceCount returns end-of-time when no worker gathers the resource the cost needs. */
    public static boolean isUnreachable(int predictedReadyFrame) {
        return predictedReadyFrame >= UNREACHABLE_FRAME;
    }

    public static int deadline(int claimFrame, int predictedReadyFrame) {
        int floor = claimFrame + MIN_HOLD_FRAMES;
        if (isUnreachable(predictedReadyFrame)) {
            return floor;
        }
        int predicted = predictedReadyFrame + PREDICTION_GRACE_FRAMES;
        return Math.max(floor, Math.min(claimFrame + MAX_HOLD_FRAMES, predicted));
    }

    public static int requeuePriority(int priority) {
        if (priority > Integer.MAX_VALUE - REQUEUE_PENALTY_FRAMES) {
            return Integer.MAX_VALUE;
        }
        return priority + REQUEUE_PENALTY_FRAMES;
    }

    public boolean isOccupied() {
        return !claims.isEmpty();
    }

    public int occupancy() {
        return claims.size();
    }

    public List<Plan> claimedPlans() {
        return new ArrayList<>(claims.keySet());
    }

    public void claim(Plan plan, int currentFrame, int predictedReadyFrame) {
        claims.put(plan, new Claim(currentFrame, deadline(currentFrame, predictedReadyFrame)));
    }

    public void release(Plan plan) {
        claims.remove(plan);
    }

    public void releaseWithBackoff(Plan plan, int currentFrame) {
        if (claims.remove(plan) == null) {
            return;
        }
        backoffUntil.put(plan.getPlannedUnit(), currentFrame + BACKOFF_FRAMES);
    }

    /** Releases the oldest claim of a building type, where the executor is known but the plan is not. */
    public void releaseFirst(UnitType unitType) {
        Plan found = null;
        for (Plan plan : claims.keySet()) {
            if (plan.getPlannedUnit() == unitType) {
                found = plan;
                break;
            }
        }
        if (found != null) {
            claims.remove(found);
        }
    }

    /** Drops claims whose plan left the production pipeline without releasing the slot. */
    public void reconcile(Collection<Plan> activePlans) {
        claims.keySet().retainAll(new HashSet<>(activePlans));
    }

    public List<Plan> stalled(int currentFrame) {
        List<Plan> stalled = new ArrayList<>();
        for (Map.Entry<Plan, Claim> entry : claims.entrySet()) {
            if (currentFrame >= entry.getValue().deadline) {
                stalled.add(entry.getKey());
            }
        }
        return stalled;
    }

    public boolean isInBackoff(UnitType unitType, int currentFrame) {
        Integer until = backoffUntil.get(unitType);
        return until != null && currentFrame < until;
    }

    public int heldFrames(Plan plan, int currentFrame) {
        Claim claim = claims.get(plan);
        return claim == null ? 0 : currentFrame - claim.claimFrame;
    }

    /** Claims due for a hold report, marking each reported at the current frame. */
    public List<Plan> holdReportsDue(int currentFrame) {
        List<Plan> due = new ArrayList<>();
        for (Map.Entry<Plan, Claim> entry : claims.entrySet()) {
            Claim claim = entry.getValue();
            if (currentFrame - claim.lastReportFrame < HOLD_REPORT_INTERVAL_FRAMES) {
                continue;
            }
            claim.lastReportFrame = currentFrame;
            due.add(entry.getKey());
        }
        return due;
    }

    private static final class Claim {

        private final int claimFrame;
        private final int deadline;
        private int lastReportFrame;

        private Claim(int claimFrame, int deadline) {
            this.claimFrame = claimFrame;
            this.deadline = deadline;
            this.lastReportFrame = claimFrame;
        }
    }
}
