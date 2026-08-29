package macro.plan;

import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;

import lombok.Data;
import org.jetbrains.annotations.Nullable;
import telemetry.PlanEvents;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public abstract class Plan {

    private static final AtomicInteger NEXT_PLAN_ID = new AtomicInteger();

    private final String uuid = UUID.randomUUID().toString();

    /**
     * Short monotonic identity, assigned at construction and never reassigned. Held on the plan
     * rather than derived by a consumer so it survives every lifecycle transition and is the same
     * value whether or not telemetry is running.
     */
    private final int planId = NEXT_PLAN_ID.incrementAndGet();

    private PlanType type;
    private PlanState state = PlanState.PLANNED;

    @Nullable
    private PlanCancelSite cancelSite;

    // Lower values have higher priority, usually corresponds to frame it was planned
    protected int priority;
    private int frameStart;
    private int retries = 0;
    private int predictedReadyFrame = 0;

    @Nullable
    private TilePosition buildPosition;

    @Nullable
    private UnitType plannedUnit;

    @Nullable
    private UpgradeType plannedUpgrade;

    private int plannedUpgradeLevel;

    @Nullable
    private TechType plannedTechType;

    /**
     * Marks a hatchery planned for production rather than to claim a base. The planned unit type
     * is Zerg_Hatchery either way, so consumers that must treat an expansion differently from a
     * macro hatchery have no other signal. Rides on the plan, so it survives the late relocation
     * in ManagedUnit that can move a build position away from its intended base.
     */
    private boolean macroHatchery;

    public Plan(int priority) {
        this.priority = priority;
    }

    /**
     * Hand-written so Lombok skips generating the setter for state. Every plan state mutation in
     * the bot goes through here, which makes this the one place telemetry can observe the plan
     * lifecycle without touching a call site.
     *
     * <p>Re-assigning the state a plan already holds is not a transition and emits nothing.
     */
    public void setState(PlanState state) {
        PlanState previous = this.state;
        if (previous == state) {
            return;
        }
        this.state = state;
        PlanEvents.stateChanged(this, previous, state);
    }

    /**
     * Hand-written so Lombok skips generating the setter. The first cancellation owns the reason:
     * once a plan is cancelled a later sweep that touches it again must not overwrite the site
     * that actually destroyed it.
     */
    public void setCancelSite(PlanCancelSite cancelSite) {
        if (this.state == PlanState.CANCELLED) {
            return;
        }
        this.cancelSite = cancelSite;
    }

    /**
     * The canonical reason this plan was cancelled, or UNKNOWN while it is alive or when a call
     * site failed to name itself.
     */
    public PlanCancelReason getCancelReason() {
        return cancelSite == null ? PlanCancelReason.UNKNOWN : cancelSite.getReason();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Plan)) {
            return false;
        }

        Plan u = (Plan) o;

        return this.uuid.equals(u.getUuid());
    }

    @Override
    public int hashCode() {
        return this.uuid.hashCode();
    }

    public String getName() {
        return "PLAN";
    }

    public int mineralPrice() {
        return 0;
    }

    public int gasPrice() {
        return 0;
    }
}
