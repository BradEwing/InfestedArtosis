package unit.managed;

import planner.Plan;

public interface ManagedUnitInterface {

    UnitRole getRole();

    void setPlan(Plan plan);

    void setRole();

    void execute();
}
