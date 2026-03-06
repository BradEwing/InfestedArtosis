package unit.squad;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.Time;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Bundles up managed units that should be functioning together to perform a goal.
 */
@Data
public class Squad implements Comparable<Squad> {

    private final String id = UUID.randomUUID().toString();

    protected HashSet<ManagedUnit> members = new HashSet<>();
    @Getter(AccessLevel.NONE)
    private final Map<UnitType, Integer> composition = new HashMap<>();
    @Getter(AccessLevel.NONE)
    private int cachedSupply = 0;

    // TODO: maybe this is consolidated with retreat target
    private Position rallyPoint;

    @Getter(AccessLevel.NONE)
    private Position center;
    protected SquadStatus status;
    private Unit target = null;

    @Getter
    private CombatSimulator combatSimulator;

    protected boolean shouldDisband = false;

    private double max_dx = 0;
    private double max_dy = 0;

    protected int fightLockedUntilFrame = 0;
    protected int retreatLockedUntilFrame = 0;
    protected int containLockedUntilFrame = 0;
    @Getter
    protected int containStartFrame = 0;
    protected Time fightHysteresis = new Time(0, 3);
    protected Time retreatHysteresis = new Time(0, 5);
    protected Time containHysteresis = new Time(0, 5);

    private static final double SMOOTHING_ALPHA = 0.85;

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof Squad)) {
            return false;
        }

        Squad s = (Squad) other;

        return this.id.equals(s.getId());
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    public int radius() {
        final double d = Math.sqrt((max_dx * max_dx) + (max_dy * max_dy));
        return (int) d;
    }

    public int distance(Squad other) {
        if (center == null || other == null) {
            return 0;
        }
        return (int) center.getDistance(other.getCenter());
    }

    public int distance(ManagedUnit managedUnit) {
        try {
            return (int) center.getDistance(managedUnit.getUnit().getPosition());
        } catch (Exception e) {
            return 0;
        }
    }

    public void onFrame() {
        calculateCenter();
        checkRegroup();
    }

    public void addUnit(ManagedUnit managedUnit) {
        members.add(managedUnit);
        UnitType type = managedUnit.getUnitType();
        composition.merge(type, 1, Integer::sum);
        cachedSupply += type.supplyRequired();
    }

    public void removeUnit(ManagedUnit managedUnit) {
        members.remove(managedUnit);
        UnitType type = managedUnit.getUnitType();
        int count = composition.getOrDefault(type, 0) - 1;
        if (count <= 0) {
            composition.remove(type);
        } else {
            composition.put(type, count);
        }
        cachedSupply = Math.max(0, cachedSupply - type.supplyRequired());
    }

    public int size() { 
        return members.size(); 
    }

    public boolean containsManagedUnit(ManagedUnit managedUnit) {
        return members.contains(managedUnit);
    }

    public void merge(Squad other) {
        for (ManagedUnit managedUnit: other.getMembers()) {
            addUnit(managedUnit);
        }
    }

    public Position getCenter() {
        if (center == null) {
            calculateCenter();
        }

        return center;
    }

    private void calculateCenter() {
        int x;
        int y;
        x = y = 0;

        for (ManagedUnit managedUnit: members) {
            Position position = managedUnit.getUnit().getPosition();
            x += position.getX();
            y += position.getY();
        }

        if (members.size() == 0) {
            this.center = new Position(0, 0);
            this.shouldDisband = true;
            return;
        }

        Position rawCenter = new Position(x / members.size(), y / members.size());

        if (this.center == null) {
            this.center = rawCenter;
        } else {
            int smoothX = (int)(this.center.getX() * SMOOTHING_ALPHA + rawCenter.getX() * (1 - SMOOTHING_ALPHA));
            int smoothY = (int)(this.center.getY() * SMOOTHING_ALPHA + rawCenter.getY() * (1 - SMOOTHING_ALPHA));
            this.center = new Position(smoothX, smoothY);
        }
    }


    private void checkRegroup() {
        boolean grouped = true;
        if (status == SquadStatus.REGROUP) {
            if (grouped) {
                status = SquadStatus.FIGHT;
                for (ManagedUnit u: members) {
                    u.setRole(UnitRole.FIGHT);
                }
                return;
            }
            for (ManagedUnit u: members) {
                u.setRallyPoint(center);
            }
        }
    }

    @Override
    public int compareTo(@NotNull Squad o) {
        return Integer.compare(this.size(), o.size());
    }

    /**
     * Returns true if this squad should be disbanded due to lack of targets.
     */
    public boolean shouldDisband() {
        return shouldDisband;
    }

    public int getSupply() {
        return cachedSupply;
    }

    public Map<UnitType, Integer> getComposition() {
        return composition;
    }

    public int getCountOf(UnitType type) {
        return composition.getOrDefault(type, 0);
    }

    public boolean hasOnly(UnitType type) {
        return composition.size() == 1 && composition.containsKey(type);
    }

    public boolean isGroundSquad() {
        return false;
    }

    public boolean isAirSquad() {
        return false;
    }

    public boolean isFightLocked(int currentFrame) {
        return currentFrame < fightLockedUntilFrame;
    }

    public boolean isRetreatLocked(int currentFrame) {
        return currentFrame < retreatLockedUntilFrame;
    }

    public void startFightLock(int currentFrame) {
        fightLockedUntilFrame = currentFrame + fightHysteresis.getFrames();
    }

    public void startRetreatLock(int currentFrame) {
        retreatLockedUntilFrame = currentFrame + retreatHysteresis.getFrames();
    }

    public boolean isContainLocked(int currentFrame) {
        return currentFrame < containLockedUntilFrame;
    }

    public void startContainLock(int currentFrame) {
        containLockedUntilFrame = currentFrame + containHysteresis.getFrames();
        if (containStartFrame == 0) {
            containStartFrame = currentFrame;
        }
    }

    public void clearContainStart() {
        containStartFrame = 0;
        containLockedUntilFrame = 0;
    }
}
