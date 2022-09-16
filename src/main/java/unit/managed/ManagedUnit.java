package unit.managed;

import unit.UnitRole;

public interface ManagedUnit {

    UnitRole getRole();

    void setRole();

    void execute();
}
