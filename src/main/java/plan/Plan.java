package plan;

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
    private int priority;
    private int frameStart;
    private int retries = 0;
    private int predictedReadyFrame = 0;

    // Block other plannedItem types in the queue
    private boolean blockOtherPlans;

    @Nullable
    private TilePosition buildPosition;

    @Nullable
    private UnitType plannedUnit;

    @Nullable
    private UpgradeType plannedUpgrade;

    @Nullable
    private TechType plannedTechType;

    public Plan(UnitType unitType, int priority, boolean isBuilding, boolean isBlocking) {
        this.priority = priority;
        this.plannedUnit = unitType;
        this.type = isBuilding ? PlanType.BUILDING : PlanType.UNIT;
        this.blockOtherPlans = isBlocking;
    }

    public Plan(UnitType unitType, int priority, boolean isBuilding, boolean isBlocking, TilePosition buildPosition) {
        this.priority = priority;
        this.plannedUnit = unitType;
        this.type = isBuilding ? PlanType.BUILDING : PlanType.UNIT;
        this.buildPosition = buildPosition;
        this.blockOtherPlans = isBlocking;
    }

    public Plan(UpgradeType upgrade, int priority, boolean isBlocking) {
        this.priority = priority;
        this.plannedUpgrade = upgrade;
        this.type = PlanType.UPGRADE;
        this.blockOtherPlans = isBlocking;
    }

    public Plan(TechType techType, int priority, boolean isBlocking) {
        this.priority = priority;
        this.plannedTechType = techType;
        this.type = PlanType.TECH;
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
        if (plannedUpgrade != null) {
            return plannedUpgrade.toString();
        } else if (plannedUnit != null) {
            return plannedUnit.toString();
        } else if (plannedTechType != null) {
            return plannedTechType.toString();
        } else {
            return "NULL";
        }
    }

    public int mineralPrice() {
        switch(this.type) {
            case BUILDING:
            case UNIT:
                return plannedUnit.mineralPrice();
            case UPGRADE:
                return plannedUpgrade.mineralPrice();
        }

        return 0;
    }
}
