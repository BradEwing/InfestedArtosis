package macro.plan;

import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.ToString;
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

    /**
     * The creep colony plan a Sunken or Spore plan morphs. The pair is formed when both plans are
     * queued and carries through fulfilment, so the morph follows the colony its pair paid for
     * rather than whichever colony happens to be free. Null once the morph adopts another colony.
     */
    @Nullable
    @ToString.Exclude
    private Plan pairedColonyPlan;

    /**
     * The base a Sunken or Spore plan reserved a colony slot at when it was planned. Captured at
     * reserve time rather than derived from the build position: a morph that adopts another
     * colony has its build position rewritten to the adopted tile, so the position no longer
     * names the base holding the reservation. Cleared once the reservation is released or
     * consumed, which makes releasing it twice a no-op rather than a silent decrement of some
     * other pair's reservation.
     */
    @Nullable
    @ToString.Exclude
    private Base reservedColonyBase;

    public Plan(int priority) {
        this.priority = priority;
    }

    /**
     * The tile this plan expects to find its creep colony on. A paired plan's build position can
     * move after the pair is formed, so the morph reads the pair's position rather than its own copy.
     */
    public TilePosition claimedColonyTile() {
        if (pairedColonyPlan != null && pairedColonyPlan.getBuildPosition() != null) {
            return pairedColonyPlan.getBuildPosition();
        }
        return getBuildPosition();
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

    /** Records a reason more specific than the source's default; the first cancellation wins. */
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
