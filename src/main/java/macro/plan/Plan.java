package macro.plan;

import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;

import lombok.Data;
import org.jetbrains.annotations.Nullable;
import telemetry.PlanEvents;

import java.util.UUID;

@Data
public abstract class Plan {
    private final String uuid = UUID.randomUUID().toString();

    private PlanType type;
    private PlanState state = PlanState.PLANNED;

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
     */
    public void setState(PlanState state) {
        PlanState previous = this.state;
        this.state = state;
        PlanEvents.stateChanged(this, previous, state);
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
