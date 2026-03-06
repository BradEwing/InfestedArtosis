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

/**
 * Combat simulator inspired by McRave's Horizon.
 * https://github.com/Cmccrave/Horizon
 */
public class HorizonCombatSimulator implements CombatSimulator {

    private static final double MAX_ENGAGEMENT_RADIUS = 768.0;
    private static final double WORKER_STRENGTH_DIVISOR = 10.0;
    private static final double HEIGHT_BONUS = 1.15;
    private static final double ENGAGE_THRESHOLD = 1.0;
    private static final double RETREAT_THRESHOLD = 0.7;

    @Getter
    private final Map<String, DebugSnapshot> lastSnapshots = new HashMap<>();

    @Override
    public CombatResult evaluate(Squad squad, Map<Squad, Double> adjacentSquads, GameState gameState) {
        Position squadCenter = squad.getCenter();
        if (squadCenter == null) return CombatResult.RETREAT;

        boolean airSquad = squad.isAirSquad();
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        boolean enemyHasDetection = enemyHasNearbyDetection(tracker, squadCenter);
        DebugSnapshot snapshot = new DebugSnapshot();
        snapshot.setSquadCenter(squadCenter);

        double friendlyGroundStr = 0;
        double friendlyAirStr = 0;

        for (ManagedUnit mu : squad.getMembers()) {
            if (mu.getUnitType() == UnitType.Zerg_Overlord) continue;
            double str = computeFriendlyStrength(mu, squadCenter, enemyHasDetection);
            snapshot.getFriendlyUnits().add(new UnitDebugEntry(mu.getUnit().getPosition(), mu.getUnitType(), str, false));
            if (mu.getUnitType().isFlyer()) {
                friendlyAirStr += str;
            } else {
                friendlyGroundStr += str;
            }
        }

        if (adjacentSquads != null) {
            for (Map.Entry<Squad, Double> entry : adjacentSquads.entrySet()) {
                Squad adjSquad = entry.getKey();
                double distance = entry.getValue();
                double weight = distanceWeight(distance);
                for (ManagedUnit mu : adjSquad.getMembers()) {
                    if (mu.getUnitType() == UnitType.Zerg_Overlord) continue;
                    double str = computeFriendlyStrength(mu, squadCenter, enemyHasDetection) * weight;
                    snapshot.getFriendlyUnits().add(new UnitDebugEntry(mu.getUnit().getPosition(), mu.getUnitType(), str, true));
                    if (mu.getUnitType().isFlyer()) {
                        friendlyAirStr += str;
                    } else {
                        friendlyGroundStr += str;
                    }
                }
            }
        }

        double enemyGroundStr = 0;
        double enemyAntiAirStr = 0;

        for (ObservedUnit ou : tracker.getLivingObservedUnits()) {
            Position pos = ou.getUnit().isVisible() ? ou.getUnit().getPosition() : ou.getLastKnownLocation();
            if (pos == null) continue;
            double dist = squadCenter.getDistance(pos);
            if (dist > MAX_ENGAGEMENT_RADIUS) continue;

            UnitType type = ou.getUnitType();
            if (type.isBuilding() && !ou.isCompleted()) continue;
            double hpWeight = hpWeighting(ou.getLastKnownHitPoints(), ou.getLastKnownShields(),
                    type.maxHitPoints(), type.maxShields());
            double distWeight = distanceWeight(dist);
            double heightMod = 1.0;
            if (!type.isFlyer() && isRanged(type) && ou.getLastKnownGroundHeight() > 0) {
                heightMod = HEIGHT_BONUS;
            }

            double groundBase = UnitStrength.groundToGround(type) + UnitStrength.airToGround(type);
            double antiAirBase = UnitStrength.antiAirStrength(type);
            if (type.isWorker()) {
                groundBase /= WORKER_STRENGTH_DIVISOR;
                antiAirBase /= WORKER_STRENGTH_DIVISOR;
            }

            double groundEnemyStr = groundBase * hpWeight * distWeight * heightMod;
            double aaEnemyStr = antiAirBase * hpWeight * distWeight * heightMod;
            enemyGroundStr += groundEnemyStr;
            enemyAntiAirStr += aaEnemyStr;

            double displayStr = airSquad ? aaEnemyStr : groundEnemyStr;
            snapshot.getEnemyUnits().add(new UnitDebugEntry(pos, type, displayStr, false));
        }

        double overallRatio;
        if (airSquad) {
            overallRatio = friendlyAirStr / Math.max(enemyAntiAirStr, 0.01);
            snapshot.setGroundRatio(0);
            snapshot.setCombinedRatio(overallRatio);
        } else {
            double groundRatio = friendlyGroundStr / Math.max(enemyGroundStr, 0.01);
            double totalFriendly = friendlyGroundStr + friendlyAirStr;
            double totalEnemy = enemyGroundStr + enemyAntiAirStr;
            double combinedRatio = totalFriendly / Math.max(totalEnemy, 0.01);
            overallRatio = Math.max(groundRatio, combinedRatio);
            snapshot.setGroundRatio(groundRatio);
            snapshot.setCombinedRatio(combinedRatio);
        }

        snapshot.setFriendlyTotal(friendlyGroundStr + friendlyAirStr);
        snapshot.setEnemyTotal(airSquad ? enemyAntiAirStr : enemyGroundStr);
        snapshot.setOverallRatio(overallRatio);

        CombatResult result;
        if (overallRatio >= ENGAGE_THRESHOLD) {
            result = CombatResult.ENGAGE;
        } else if (overallRatio < RETREAT_THRESHOLD) {
            result = CombatResult.RETREAT;
        } else {
            result = CombatResult.REGROUP;
        }

        snapshot.setResult(result);
        lastSnapshots.put(squad.getId(), snapshot);

        return result;
    }

    private double computeFriendlyStrength(ManagedUnit mu, Position engagementCenter, boolean enemyHasDetection) {
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
            if (!enemyHasDetection) {
                cloak = 2.0;
            }
        }

        double prepPenalty = 1.0;
        if (type == UnitType.Zerg_Lurker && !unit.isBurrowed()) {
            prepPenalty = 0.3;
        }

        return base * hpWeight * distWeight * cloak * prepPenalty;
    }

    private boolean enemyHasNearbyDetection(ObservedUnitTracker tracker, Position center) {
        for (ObservedUnit ou : tracker.getLivingObservedUnits()) {
            if (!ou.getUnitType().isDetector()) continue;
            Position pos = ou.getUnit().isVisible() ? ou.getUnit().getPosition() : ou.getLastKnownLocation();
            if (pos != null && center.getDistance(pos) <= MAX_ENGAGEMENT_RADIUS) {
                return true;
            }
        }
        return false;
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

    @Getter
    @lombok.RequiredArgsConstructor
    public static class UnitDebugEntry {
        private final Position position;
        private final UnitType type;
        private final double strength;
        private final boolean adjacent;
    }

    @Getter
    @lombok.Setter
    public static class DebugSnapshot {
        private Position squadCenter;
        private final List<UnitDebugEntry> friendlyUnits = new ArrayList<>();
        private final List<UnitDebugEntry> enemyUnits = new ArrayList<>();
        private double friendlyTotal;
        private double enemyTotal;
        private double groundRatio;
        private double combinedRatio;
        private double overallRatio;
        private CombatResult result;
    }
}
