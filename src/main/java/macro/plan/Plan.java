package macro.plan;

import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import telemetry.PlanEvents;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public abstract class Plan {

    private static final AtomicInteger NEXT_PLAN_ID = new AtomicInteger();

    private final String uuid = UUID.randomUUID().toString();

    private final int planId = NEXT_PLAN_ID.incrementAndGet();

    private PlanType type;
    private PlanState state = PlanState.PLANNED;

    @Nullable
    private PlanCancelSource cancelSource;

    @Nullable
    @Setter(AccessLevel.NONE)
    private PlanCancelReason cancelReason;

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
     * <p>Assigning the current state emits no transition.
     */
    public void setState(PlanState state) {
        PlanState previous = this.state;
        if (previous == state) {
            return;
        }
        this.state = state;
        PlanEvents.stateChanged(this, previous, state);
    }

    public void setCancelSource(PlanCancelSource cancelSource) {
        setCancelSource(cancelSource, cancelSource.getReason());
    }

    /**
     * Stamps the source with a reason more specific than the source's default, so a sweep that
     * checks several predicates records the one that failed. The first cancellation owns both.
     */
    public void setCancelSource(PlanCancelSource cancelSource, PlanCancelReason cancelReason) {
        if (this.state == PlanState.CANCELLED) {
            return;
        }
        this.cancelSource = cancelSource;
        this.cancelReason = cancelReason;
    }

    public PlanCancelReason getCancelReason() {
        return cancelReason == null ? PlanCancelReason.UNKNOWN : cancelReason;
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
