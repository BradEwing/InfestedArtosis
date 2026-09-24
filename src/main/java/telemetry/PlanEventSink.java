package telemetry;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.BuilderThreat;
import macro.plan.BuilderDispatchDecision;
import macro.plan.BuilderLossReason;
import macro.plan.BuilderReading;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import strategy.buildorder.GasBoundHiveTech;
import strategy.buildorder.LarvaBoundMacroHatchery;

import java.util.List;

/**
 * Receives plan lifecycle events. Implementations are registered with {@link PlanEvents} and must
 * never throw: they run inside the production hot path, where an escaped exception kills the JVM.
 */
public interface PlanEventSink {

    void onEnqueue(Plan plan);

    void onStateChange(Plan plan, PlanState from, PlanState to);

    void onBlocked(Plan plan, PlanBlocker blocker);

    default void onEnqueueAll(List<Plan> plans) {
        for (Plan plan : plans) {
            onEnqueue(plan);
        }
    }

    /**
     * A plan has waited in PLANNED past the stale threshold and no longer counts as gas demand.
     * Called on every scan that finds it stale.
     */
    default void onStale(Plan plan) {
    }

    /** A building plan still holds the build-ahead slot. */
    default void onBuildAheadHold(Plan holder, int heldFrames, int starvedBehind) {
    }

    /** A building plan lost the build-ahead slot without placing its building. */
    default void onBuildAheadEvict(Plan holder, int heldFrames, int starvedBehind) {
    }

    /** A building plan gave the build-ahead slot to an emergency defence plan. */
    default void onBuildAheadYield(Plan holder, int heldFrames, Plan emergency) {
    }

    /** A build order wanted a unit but created no plan because the blocker would sweep it. */
    default void onWithheld(UnitType unitType, PlanBlocker blocker) {
    }

    /**
     * A build order evaluated the larva-bound macro hatchery request.
     *
     * @param gate the gate the request stopped on, or TRIGGER
     * @param techReady the build's tech condition as the request read it
     * @param hatcheries completed larva-producing hatcheries as the request read them
     * @param outstandingMacroHatcheries macro hatchery plans in flight plus macro hatcheries under construction
     */
    default void onMacroHatcheryGate(LarvaBoundMacroHatchery.Gate gate, boolean techReady, int hatcheries,
                                     int outstandingMacroHatcheries) {
    }

    /**
     * A build order evaluated a Hive-branch structure against the gas bank.
     *
     * @param gate the gate the request stopped on, or TRIGGER
     * @param structure the structure the gate guards
     * @param availableGas gas mined and not reserved by a queued plan, as the request read it
     * @param requiredGas the branch bar the bank is measured against
     * @param extractorsCompleted finished Extractors, carried as a diagnostic: no gate reads it
     */
    default void onHiveTechGate(GasBoundHiveTech.Gate gate, UnitType structure, int availableGas,
                                int requiredGas, int extractorsCompleted) {
    }

    /** A unit was cancelled outside the plan system, so no plan transition records the cancellation. */
    default void onUnplannedCancel(UnitType unitType, PlanCancelSource cancelSource) {
    }

    /** A stalled builder was sent to mine the blocking mineral at this position. */
    default void onBlockerDivert(Plan plan, Position mineral) {
    }

    /**
     * The dispatch gate evaluated a scheduled drone-built building for departure, or re-evaluated
     * one already walking.
     *
     * @param plan the building plan
     * @param decision what the gate did with it
     * @param threat the reading the decision was made on
     */
    default void onBuilderDispatchDecision(Plan plan, BuilderDispatchDecision decision, BuilderThreat threat) {
    }

    /**
     * A walking builder stopped executing its BUILDING plan for a reason other than a threat recall.
     *
     * @param plan the building plan
     * @param reason why the builder was lost
     * @param builder the lost builder as last read, or null if it was never read
     */
    default void onBuilderLost(Plan plan, BuilderLossReason reason, BuilderReading builder) {
    }

    /**
     * A new builder was dispatched for a plan that had lost its builder.
     *
     * @param plan the building plan
     * @param reason why the previous builder was lost
     * @param lost the previous builder as last read
     * @param taker the new builder, read on its dispatch frame
     */
    default void onBuilderRedispatch(Plan plan, BuilderLossReason reason, BuilderReading lost,
                                     BuilderReading taker) {
    }

    /**
     * A lost expansion builder armed a hold on expanding.
     *
     * @param lostExpansionBuilders builders lost since the last expansion that landed
     * @param expansionHeldUntilFrame frame expansions become available again
     */
    default void onExpansionBackoff(int lostExpansionBuilders, int expansionHeldUntilFrame) {
    }

    /**
     * A lost Creep Colony builder armed a hold on sunken planning at a base.
     *
     * @param base the held base's tile location
     * @param lostColonyBuilders colony builders lost at the base since a colony there last started morphing
     * @param colonyHeldUntilFrame frame the hold on the base lifts
     */
    default void onColonyBuilderBackoff(TilePosition base, int lostColonyBuilders, int colonyHeldUntilFrame) {
    }

    /**
     * StrategyTracker added a strategy to its detected set, directly or by implication, after resolving
     * supersessions for the frame.
     *
     * @param detectionLabel the detected strategy's name, followed by the evidence it was detected on when the
     *     strategy records one, e.g. ProxyGate:MAIN_EMPTY
     */
    default void onStrategyDetected(String detectionLabel) {
    }

    /**
     * One of our bases lost its hatchery.
     *
     * @param base the base's tile location
     * @param innerBase whether the base was our main or a natural
     */
    default void onBaseLost(TilePosition base, boolean innerBase) {
    }
}
