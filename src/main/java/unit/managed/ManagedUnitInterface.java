package unit.managed;

import bwapi.TilePosition;
import bwapi.Unit;
import planner.Plan;

import java.util.List;

public interface ManagedUnitInterface {

    UnitRole getRole();

    void setPlan(Plan plan);

    void setRole();

    void execute();

    void assignClosestEnemyAsFightTarget(List<Unit> enemies, TilePosition backupScoutPosition);
}
