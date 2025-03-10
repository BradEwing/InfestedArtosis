package macro.plan;

import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// TODO: Refactor with subclasses
@Data
public class Plan {
    private final String uuid = UUID.randomUUID().toString();

    private PlanType type;
    private PlanState state = PlanState.PLANNED;

    // Simple prioritization, will increment elsewhere
    protected int priority;
    private int frameStart;
    private int retries = 0;
    private int predictedReadyFrame = 0;

    // Block other plannedItem types in the queue
    protected boolean blockOtherPlans;

    @Nullable
    private TilePosition buildPosition;

    @Nullable
    private UnitType plannedUnit;

    @Nullable
    private UpgradeType plannedUpgrade;

    @Nullable
    private TechType plannedTechType;

    public Plan(int priority, boolean isBlocking) {
        this.priority = priority;
        this.blockOtherPlans = isBlocking;
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
}
