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

    /**
     * The ceiling a refreshed hold can never pass, however far the income prediction slides.
     * Twice the hold a claim is granted: a plan whose income recovers is carried to its builder's
     * launch, and one whose income is gone is still evicted.
     */
    static final int TOTAL_HOLD_FRAMES = 24 * 120;

    static final int PREDICTION_GRACE_FRAMES = 24 * 15;

    static final int BACKOFF_FRAMES = 24 * 15;

    static final int HOLD_REPORT_INTERVAL_FRAMES = 24 * 10;

    private static final int UNREACHABLE_FRAME = Integer.MAX_VALUE - (TOTAL_HOLD_FRAMES + PREDICTION_GRACE_FRAMES);

    private final Map<Plan, Claim> claims = new LinkedHashMap<>();

    private final Map<Plan, Backoff> backoffs = new HashMap<>();

    /** ResourceCount returns end-of-time when no worker gathers the resource the cost needs. */
    public static boolean isUnreachable(int predictedReadyFrame) {
        return predictedReadyFrame >= UNREACHABLE_FRAME;
    }

    public static int deadline(int claimFrame, int predictedReadyFrame) {
        return deadline(claimFrame, predictedReadyFrame, 0);
    }

    /**
     * The hold never expires before the builder's own travel estimate plus the prediction grace.
     * An affordable plan predicts ready in 20 frames, so without that floor the deadline lands
     * inside the walk it is timing and evicts a builder that is still on its way.
     */
    public static int deadline(int claimFrame, int predictedReadyFrame, int travelFrames) {
        return deadline(claimFrame, predictedReadyFrame, travelFrames, MAX_HOLD_FRAMES);
    }

    private static int deadline(int claimFrame, int predictedReadyFrame, int travelFrames, int cap) {
        int travel = Math.max(0, Math.min(cap, travelFrames));
        int floor = claimFrame + MIN_HOLD_FRAMES;
        if (travel > 0) {
            floor = Math.max(floor, claimFrame + Math.min(cap, travel + PREDICTION_GRACE_FRAMES));
        }
        if (isUnreachable(predictedReadyFrame)) {
            return floor;
        }
        int predicted = predictedReadyFrame + PREDICTION_GRACE_FRAMES;
        return Math.max(floor, Math.min(claimFrame + cap, predicted));
    }

    /**
     * True when the builder could not be dispatched before the longest hold the slot can reach.
     *
     * <p>PlanManager releases the builder on the first frame past
     * {@code predictedReadyFrame - travelFrames}. A claim that only reaches that frame as its hold
     * expires is evicted on the very frame its dispatch gate opens, so it is refused instead.
     */
    public static boolean dispatchOutlastsHold(int claimFrame, int predictedReadyFrame, int travelFrames) {
        if (isUnreachable(predictedReadyFrame)) {
            return true;
        }
        int travel = Math.max(0, Math.min(TOTAL_HOLD_FRAMES, travelFrames));
        return predictedReadyFrame - travel >= claimFrame + TOTAL_HOLD_FRAMES;
    }

    /**
     * Re-times a hold against a refreshed income prediction.
     *
     * <p>A claim is timed on the income the bot had when it was taken. Gatherers die and drones are
     * reassigned, so that estimate decays under a plan still waiting for its builder to launch, and
     * the plan rides to the claim-time cap and is evicted with its drone parked on minerals. The
     * deadline follows the refreshed estimate but never shortens and never passes
     * {@code claimFrame + TOTAL_HOLD_FRAMES}.
     */
    public void extend(Plan plan, int predictedReadyFrame, int travelFrames) {
        Claim claim = claims.get(plan);
        if (claim == null) {
            return;
        }
        int refreshed = deadline(claim.claimFrame, predictedReadyFrame, travelFrames, TOTAL_HOLD_FRAMES);
        claim.deadline = Math.min(claim.claimFrame + TOTAL_HOLD_FRAMES, Math.max(claim.deadline, refreshed));
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
        claim(plan, currentFrame, predictedReadyFrame, 0);
    }

    /**
     * A plan claiming during its backoff resumes the hold it was released from rather than starting
     * a new one, so an eviction cannot restart the hold clock of a plan that re-claims at once.
     */
    public void claim(Plan plan, int currentFrame, int predictedReadyFrame, int travelFrames) {
        Backoff backoff = activeBackoff(plan, currentFrame);
        if (backoff == null) {
            int deadline = deadline(currentFrame, predictedReadyFrame, travelFrames);
            claims.put(plan, new Claim(currentFrame, deadline, currentFrame));
            return;
        }
        int deadline = deadline(backoff.claimFrame, predictedReadyFrame, travelFrames, TOTAL_HOLD_FRAMES);
        claims.put(plan, new Claim(backoff.claimFrame, deadline, currentFrame));
    }

    public void release(Plan plan) {
        claims.remove(plan);
    }

    public void releaseWithBackoff(Plan plan, int currentFrame) {
        Claim claim = claims.remove(plan);
        if (claim == null) {
            return;
        }
        backoffs.values().removeIf(backoff -> currentFrame >= backoff.until);
        backoffs.put(plan, new Backoff(claim.claimFrame, currentFrame + BACKOFF_FRAMES));
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

    /** Keyed on the plan, so an eviction bars only the offender and not every plan of its type. */
    public boolean isInBackoff(Plan plan, int currentFrame) {
        return activeBackoff(plan, currentFrame) != null;
    }

    /**
     * True while a plan in backoff has no hold left to resume. Its released hold already reached
     * {@code claimFrame + TOTAL_HOLD_FRAMES}, so a resumed claim would expire on the frame it was
     * taken; the plan waits out its backoff and then claims afresh.
     */
    public boolean isHoldSpent(Plan plan, int currentFrame) {
        Backoff backoff = activeBackoff(plan, currentFrame);
        return backoff != null && currentFrame >= backoff.claimFrame + TOTAL_HOLD_FRAMES;
    }

    private Backoff activeBackoff(Plan plan, int currentFrame) {
        Backoff backoff = backoffs.get(plan);
        return backoff != null && currentFrame < backoff.until ? backoff : null;
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
        private int deadline;
        private int lastReportFrame;

        private Claim(int claimFrame, int deadline, int lastReportFrame) {
            this.claimFrame = claimFrame;
            this.deadline = deadline;
            this.lastReportFrame = lastReportFrame;
        }
    }

    private static final class Backoff {

        private final int claimFrame;
        private final int until;

        private Backoff(int claimFrame, int until) {
            this.claimFrame = claimFrame;
            this.until = until;
        }
    }
}
