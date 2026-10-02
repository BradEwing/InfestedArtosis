package macro;

import bwapi.TilePosition;
import bwapi.UnitType;
import lombok.Getter;
import macro.plan.Plan;
import macro.plan.PlanState;

/**
 * The extractor trick: a drone starts an Extractor, a larva morphs a drone on the supply the first
 * drone freed, and the Extractor is cancelled so the first drone comes back. The bot ends one drone
 * over its supply cap.
 *
 * <p>A build order arms the trick with the two plans it queued, an Extractor and the Drone that
 * waits on the freed supply. {@link ProductionManager} observes the Extractor on the geyser each
 * frame and issues the cancel once the Drone is in its egg. The Extractor plan leaves the plan
 * system the frame its drone is consumed, like every Extractor plan, so nothing the trick cancels
 * is left in MORPHING.
 *
 * <p>Every wait is bounded. An Extractor that never starts releases the trick after
 * {@link #START_TIMEOUT_FRAMES}, leaving its plan to finish as the build's real Extractor, and a
 * Drone that has not reached its egg once the Extractor has been building for
 * {@link #CANCEL_DEADLINE_FRAMES} no longer holds the cancel.
 */
public class ExtractorTrick {

    /** Frames an armed trick waits for its Extractor to start before releasing. */
    static final int START_TIMEOUT_FRAMES = 720;

    /** Frames a started Extractor may build before it is cancelled whatever the Drone is doing. */
    static final int CANCEL_DEADLINE_FRAMES = UnitType.Zerg_Extractor.buildTime() / 2;

    public enum Phase {
        IDLE,
        ARMED,
        STARTED,
        DONE
    }

    /** What {@link ProductionManager} does with the trick this frame. */
    public enum Step {
        WAIT,
        START,
        CANCEL,
        RELEASE
    }

    @Getter
    private Phase phase = Phase.IDLE;

    private Plan dronePlan;

    @Getter
    private TilePosition geyser;

    private int armedFrame;

    private int startedFrame;

    /**
     * Arms the trick. Ignored unless the trick is idle, so a build runs it at most once.
     *
     * @param extractorPlan the Extractor plan whose drone frees the supply
     * @param dronePlan the Drone plan that morphs on the freed supply
     * @param frame the current frame
     */
    public void arm(Plan extractorPlan, Plan dronePlan, int frame) {
        if (phase != Phase.IDLE) {
            return;
        }
        this.dronePlan = dronePlan;
        this.geyser = extractorPlan.getBuildPosition();
        this.armedFrame = frame;
        this.phase = Phase.ARMED;
    }

    /**
     * Marks the trick as not run: a build that skips it still answers {@link #isSettled()}.
     */
    public void skip() {
        if (phase == Phase.IDLE) {
            phase = Phase.DONE;
        }
    }

    /** True until the trick is armed or skipped. */
    public boolean isIdle() {
        return phase == Phase.IDLE;
    }

    /** True while the trick waits on its Extractor or on its Drone. */
    public boolean isRunning() {
        return phase == Phase.ARMED || phase == Phase.STARTED;
    }

    /** True once the trick has cancelled its Extractor, been released or been skipped. */
    public boolean isSettled() {
        return phase == Phase.DONE;
    }

    /**
     * Decides this frame's step and moves the trick to the phase it leads to. A CANCEL leaves the
     * trick STARTED until {@link #cancelled()} confirms the cancel command was accepted.
     *
     * @param frame the current frame
     * @param extractorOnGeyser whether our unfinished Extractor stands on the trick's geyser
     * @return the step to carry out
     */
    public Step update(int frame, boolean extractorOnGeyser) {
        Step step = step(phase, frame - armedFrame, frame - startedFrame, extractorOnGeyser,
                dronePlan == null ? null : dronePlan.getState());
        if (step == Step.START) {
            phase = Phase.STARTED;
            startedFrame = frame;
        } else if (step == Step.RELEASE) {
            phase = Phase.DONE;
        }
        return step;
    }

    /** Records that the Extractor's cancel was accepted. */
    public void cancelled() {
        phase = Phase.DONE;
    }

    /**
     * The trick's step for one frame.
     *
     * <p>Armed, the trick starts once its Extractor stands on the geyser, and releases after
     * {@link #START_TIMEOUT_FRAMES} without one. Started, it releases if the Extractor is gone,
     * and cancels once the Drone is in its egg or beyond, once the Drone plan is cancelled, or
     * once the Extractor has been building for {@link #CANCEL_DEADLINE_FRAMES}.
     *
     * @param phase the trick's phase
     * @param framesSinceArmed frames since the trick was armed
     * @param framesSinceStarted frames since the Extractor was first seen, meaningful only once STARTED
     * @param extractorOnGeyser whether our unfinished Extractor stands on the trick's geyser
     * @param droneState the Drone plan's state
     * @return the step to carry out
     */
    static Step step(Phase phase, int framesSinceArmed, int framesSinceStarted, boolean extractorOnGeyser,
                     PlanState droneState) {
        switch (phase) {
            case ARMED:
                if (extractorOnGeyser) {
                    return Step.START;
                }
                return framesSinceArmed >= START_TIMEOUT_FRAMES ? Step.RELEASE : Step.WAIT;
            case STARTED:
                if (!extractorOnGeyser) {
                    return Step.RELEASE;
                }
                return droneReleasesExtractor(droneState) || framesSinceStarted >= CANCEL_DEADLINE_FRAMES
                        ? Step.CANCEL
                        : Step.WAIT;
            default:
                return Step.WAIT;
        }
    }

    /**
     * Whether the Drone no longer needs the Extractor's supply: its egg has started, it has
     * hatched, or its plan is gone.
     *
     * @param droneState the Drone plan's state
     * @return true when the Extractor may be cancelled
     */
    static boolean droneReleasesExtractor(PlanState droneState) {
        return droneState == null
                || droneState == PlanState.MORPHING
                || droneState == PlanState.COMPLETE
                || droneState == PlanState.CANCELLED;
    }
}
