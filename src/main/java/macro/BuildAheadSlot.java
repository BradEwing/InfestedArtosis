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
 *
 * <p>A claim used to end only when the building morphed or a sweep cancelled the plan. A plan that
 * could never place its building - no income for its cost, no build position, no executor - kept
 * the slot and its mineral and gas reservation for the rest of the game, and every plan queued
 * behind it starved.
 *
 * <p>Every claim now carries a deadline derived from the income prediction that justified it. A
 * claim that outlives its deadline is stalled, and production evicts it: the reservation returns
 * to the bank and the building type is barred from the slot for a backoff, so a plan that is
 * cancelled and immediately re-derived cannot silently reserve the same cost again.
 */
public class BuildAheadSlot {

    /** Frames a claim holds the slot before its income prediction is believed at all. */
    static final int MIN_HOLD_FRAMES = 24 * 10;

    /** Ceiling on a claim, however distant its income prediction. */
    static final int MAX_HOLD_FRAMES = 24 * 60;

    /** Slack over the income prediction, covering executor travel and the morph command. */
    static final int PREDICTION_GRACE_FRAMES = 24 * 15;

    /** Frames a building type is barred from the slot after an abnormal release. */
    static final int BACKOFF_FRAMES = 24 * 15;

    /** Priority a plan gives up when it is evicted, so it re-enters behind what it starved. */
    static final int REQUEUE_PENALTY_FRAMES = 24 * 15;

    /** Frames between telemetry rows for a claim that is still held. */
    static final int HOLD_REPORT_INTERVAL_FRAMES = 24 * 10;

    private static final int UNREACHABLE_FRAME = Integer.MAX_VALUE - (MAX_HOLD_FRAMES + PREDICTION_GRACE_FRAMES);

    private final Map<Plan, Claim> claims = new LinkedHashMap<>();

    private final Map<UnitType, Integer> backoffUntil = new HashMap<>();

    /**
     * An income prediction this far out is not a prediction. ResourceCount returns the end of time
     * when no worker gathers the resource the cost needs, which is the shape of every stall in the
     * evidence: a plan that reserved gas it had no extractor for.
     */
    public static boolean isUnreachable(int predictedReadyFrame) {
        return predictedReadyFrame >= UNREACHABLE_FRAME;
    }

    /** The frame a claim made on {@code claimFrame} stops being justified. */
    public static int deadline(int claimFrame, int predictedReadyFrame) {
        int floor = claimFrame + MIN_HOLD_FRAMES;
        if (isUnreachable(predictedReadyFrame)) {
            return floor;
        }
        int predicted = predictedReadyFrame + PREDICTION_GRACE_FRAMES;
        return Math.max(floor, Math.min(claimFrame + MAX_HOLD_FRAMES, predicted));
    }

    /** Priority an evicted plan carries back onto the queue. */
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

    /**
     * Releases a claim that ended without the building being placed, and bars the building type
     * from the slot until the backoff expires.
     */
    public void releaseWithBackoff(Plan plan, int currentFrame) {
        if (claims.remove(plan) == null) {
            return;
        }
        backoffUntil.put(plan.getPlannedUnit(), currentFrame + BACKOFF_FRAMES);
    }

    /**
     * Releases the oldest claim of a building type. Used where the executor is known but the plan
     * is not, as when a geyser renegades into a finished extractor.
     */
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

    /** Claims whose hold is due to be reported, marking each reported at the current frame. */
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
