package unit.squad.horizon;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import lombok.Getter;
import unit.managed.ManagedUnit;
import unit.squad.CombatSimulator;
import unit.squad.Squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HorizonCombatSimulator implements CombatSimulator {

    private static final double MAX_ENGAGEMENT_RADIUS = 768.0;
    private static final double WORKER_STRENGTH_DIVISOR = 10.0;
    private static final double HEIGHT_BONUS = 1.15;
    private static final double ENGAGE_THRESHOLD = 1.0;
    private static final double RETREAT_THRESHOLD = 0.7;

    @Getter
    private final Map<String, DebugSnapshot> lastSnapshots = new HashMap<>();

    @Override
    public CombatResult evaluate(Squad squad, Set<ManagedUnit> reinforcements, GameState gameState) {
        return evaluateWithAdjacentSquads(squad, null, gameState);
    }

    public CombatResult evaluateWithAdjacentSquads(Squad squad, Map<Squad, Double> adjacentSquads, GameState gameState) {
        Position squadCenter = squad.getCenter();
        if (squadCenter == null) return CombatResult.RETREAT;

        DebugSnapshot snapshot = new DebugSnapshot();
        snapshot.squadCenter = squadCenter;

        double friendlyG2G = 0;
        double friendlyTotal = 0;

        for (ManagedUnit mu : squad.getMembers()) {
            if (mu.getUnitType() == UnitType.Zerg_Overlord) continue;
            double str = computeFriendlyStrength(mu, squadCenter);
            friendlyG2G += groundToGroundStrength(mu);
            friendlyTotal += str;
            snapshot.friendlyUnits.add(new UnitDebugEntry(mu.getUnit().getPosition(), mu.getUnitType(), str, false));
        }

        if (adjacentSquads != null) {
            for (Map.Entry<Squad, Double> entry : adjacentSquads.entrySet()) {
                Squad adjSquad = entry.getKey();
                double distance = entry.getValue();
                double weight = distanceWeight(distance);
                for (ManagedUnit mu : adjSquad.getMembers()) {
                    if (mu.getUnitType() == UnitType.Zerg_Overlord) continue;
                    double str = computeFriendlyStrength(mu, squadCenter) * weight;
                    friendlyG2G += groundToGroundStrength(mu) * weight;
                    friendlyTotal += str;
                    snapshot.friendlyUnits.add(new UnitDebugEntry(mu.getUnit().getPosition(), mu.getUnitType(), str, true));
                }
            }
        }

        double enemyG2G = 0;
        double enemyTotal = 0;

        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        for (ObservedUnit ou : tracker.getLivingObservedUnits()) {
            Position pos = ou.getUnit().isVisible() ? ou.getUnit().getPosition() : ou.getLastKnownLocation();
            if (pos == null) continue;
            double dist = squadCenter.getDistance(pos);
            if (dist > MAX_ENGAGEMENT_RADIUS) continue;

            UnitType type = ou.getUnitType();
            double base = UnitStrength.totalStrength(type);
            if (type.isWorker()) {
                base /= WORKER_STRENGTH_DIVISOR;
            }

            double hpWeight = hpWeighting(ou.getLastKnownHitPoints(), ou.getLastKnownShields(),
                    type.maxHitPoints(), type.maxShields());
            double distWeight = distanceWeight(dist);
            double heightMod = 1.0;
            if (!type.isFlyer() && isRanged(type) && ou.getLastKnownGroundHeight() > 0) {
                heightMod = HEIGHT_BONUS;
            }

            double str = base * hpWeight * distWeight * heightMod;
            enemyG2G += UnitStrength.groundToGround(type) * hpWeight * distWeight * heightMod;
            enemyTotal += str;
            snapshot.enemyUnits.add(new UnitDebugEntry(pos, type, str, false));
        }

        double groundRatio = friendlyG2G / Math.max(enemyG2G, 0.01);
        double combinedRatio = friendlyTotal / Math.max(enemyTotal, 0.01);
        double overallRatio = Math.max(groundRatio, combinedRatio);

        snapshot.friendlyTotal = friendlyTotal;
        snapshot.enemyTotal = enemyTotal;
        snapshot.groundRatio = groundRatio;
        snapshot.combinedRatio = combinedRatio;
        snapshot.overallRatio = overallRatio;

        CombatResult result;
        if (overallRatio >= ENGAGE_THRESHOLD) {
            result = CombatResult.ENGAGE;
        } else if (overallRatio < RETREAT_THRESHOLD) {
            result = CombatResult.RETREAT;
        } else {
            result = CombatResult.REGROUP;
        }

        snapshot.result = result;
        lastSnapshots.put(squad.getId(), snapshot);

        return result;
    }

    private double computeFriendlyStrength(ManagedUnit mu, Position engagementCenter) {
        Unit unit = mu.getUnit();
        UnitType type = unit.getType();
        double base = UnitStrength.totalStrength(type);

        int hp = unit.getHitPoints();
        int shields = unit.getShields();
        double hpWeight = hpWeighting(hp, shields, type.maxHitPoints(), type.maxShields());

        double dist = unit.getPosition().getDistance(engagementCenter);
        double distWeight = distanceWeight(dist);

        double cloak = 1.0;
        if ((type == UnitType.Zerg_Lurker && unit.isBurrowed()) || type == UnitType.Protoss_Dark_Templar) {
            if (!unit.isDetected()) {
                cloak = 2.0;
            }
        }

        double prepPenalty = 1.0;
        if (type == UnitType.Zerg_Lurker && !unit.isBurrowed()) {
            prepPenalty = 0.3;
        }

        return base * hpWeight * distWeight * cloak * prepPenalty;
    }

    private double groundToGroundStrength(ManagedUnit mu) {
        UnitType type = mu.getUnit().getType();
        double base = UnitStrength.groundToGround(type);
        double hpWeight = hpWeighting(mu.getUnit().getHitPoints(), mu.getUnit().getShields(),
                type.maxHitPoints(), type.maxShields());
        return base * hpWeight;
    }

    private double hpWeighting(int hp, int shields, int maxHp, int maxShields) {
        int denominator = 3 * maxHp + maxShields;
        if (denominator == 0) return 1.0;
        return (double) (3 * hp + shields) / denominator;
    }

    private double distanceWeight(double distance) {
        if (distance <= 256) return 1.0;
        if (distance <= 512) return 1.0 - 0.5 * (distance - 256) / 256;
        if (distance <= MAX_ENGAGEMENT_RADIUS) return 0.5 - 0.25 * (distance - 512) / 256;
        return 0;
    }

    private boolean isRanged(UnitType type) {
        if (type.groundWeapon() != null && type.groundWeapon().maxRange() > 32) return true;
        return type.airWeapon() != null && type.airWeapon().maxRange() > 32;
    }

    public static class UnitDebugEntry {
        public final Position position;
        public final UnitType type;
        public final double strength;
        public final boolean isAdjacent;

        public UnitDebugEntry(Position position, UnitType type, double strength, boolean isAdjacent) {
            this.position = position;
            this.type = type;
            this.strength = strength;
            this.isAdjacent = isAdjacent;
        }
    }

    public static class DebugSnapshot {
        public Position squadCenter;
        public final List<UnitDebugEntry> friendlyUnits = new ArrayList<>();
        public final List<UnitDebugEntry> enemyUnits = new ArrayList<>();
        public double friendlyTotal;
        public double enemyTotal;
        public double groundRatio;
        public double combinedRatio;
        public double overallRatio;
        public CombatResult result;
    }
}
