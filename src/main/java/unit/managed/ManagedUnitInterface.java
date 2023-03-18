package unit.managed;

import planner.PlannedItem;

public interface ManagedUnitInterface {

    UnitRole getRole();

    void setPlan(PlannedItem plan);

    void setRole();

    void execute();
}
