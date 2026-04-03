package unit.squad.horizon;

import bwapi.DamageType;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import bwapi.WeaponType;
import info.GameState;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import lombok.Getter;
import unit.managed.ManagedUnit;
import unit.squad.CombatSimulator;
import unit.squad.Squad;
import util.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Combat simulator inspired by McRave's Horizon.
 * https://github.com/Cmccrave/McRave/tree/bfbce3c74d240f4a2cbfe6767c8172af842049be/Source/Horizon
 */
public class HorizonCombatSimulator implements CombatSimulator {

    private static final double MAX_ENGAGEMENT_RADIUS = 320;
    private static final double WORKER_STRENGTH_DIVISOR = 10.0;
    private static final double HEIGHT_BONUS = 1.15;
    private static final Time RECENTLY_SEEN_THRESHOLD = new Time(0, 5);
    private static final double ENGAGE_THRESHOLD = 1.0;
    private static final double RETREAT_THRESHOLD = 0.7;

    @Getter
    private final Map<String, DebugSnapshot> lastSnapshots = new HashMap<>();

    @Override
    public CombatResult evaluate(Squad squad, Map<Squad, Double> adjacentSquads, GameState gameState) {
        Position squadCenter = squad.getCenter();
        if (squadCenter == null) return CombatResult.RETREAT;

        boolean airSquad = squad.isAirSquad();
        int currentFrame = gameState.getGame().getFrameCount();
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        boolean enemyHasDetection = enemyHasNearbyDetection(tracker, squadCenter, currentFrame);
        DebugSnapshot snapshot = new DebugSnapshot();
        snapshot.setSquadCenter(squadCenter);

        double friendlyGroundStr = 0;
        double friendlyAirStr = 0;

        for (ManagedUnit mu : squad.getMembers()) {
            if (mu.getUnitType() == UnitType.Zerg_Overlord) continue;
            double str = computeFriendlyStrength(mu, squadCenter, enemyHasDetection);
            snapshot.getFriendlyUnits().add(new UnitDebugEntry(mu.getUnit().getPosition(), mu.getUnitType(), str, false, false));
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
                    snapshot.getFriendlyUnits().add(new UnitDebugEntry(mu.getUnit().getPosition(), mu.getUnitType(), str, true, false));
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

        Map<UnitSizeType, Double> friendlySizeProportions = sizeProportions(squad, adjacentSquads);

        for (ObservedUnit ou : tracker.getLivingObservedUnits()) {
            UnitType type = ou.getUnitType();
            boolean visible = ou.getUnit().isVisible();
            if (!visible && !isPositionalUnit(type)) {
                int framesSinceObserved = currentFrame - ou.getLastObservedFrame().getFrames();
                if (framesSinceObserved > RECENTLY_SEEN_THRESHOLD.getFrames()) continue;
            }

            Position pos = visible ? ou.getUnit().getPosition() : ou.getLastKnownLocation();
            if (pos == null) continue;
            double dist = squadCenter.getDistance(pos);
            if (dist > MAX_ENGAGEMENT_RADIUS) continue;

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

            groundBase *= weightedEffectiveness(groundDamageType(type), friendlySizeProportions);
            antiAirBase *= weightedEffectiveness(airDamageType(type), friendlySizeProportions);

            double groundEnemyStr = groundBase * hpWeight * distWeight * heightMod;
            double aaEnemyStr = antiAirBase * hpWeight * distWeight * heightMod;
            enemyGroundStr += groundEnemyStr;
            enemyAntiAirStr += aaEnemyStr;

            double displayStr = airSquad ? aaEnemyStr : groundEnemyStr;
            snapshot.getEnemyUnits().add(new UnitDebugEntry(pos, type, displayStr, false, !visible));
        }

        if (!snapshot.getEnemyUnits().isEmpty()) {
            double ex = 0; 
            double ey = 0;
            for (UnitDebugEntry e : snapshot.getEnemyUnits()) {
                ex += e.getPosition().getX();
                ey += e.getPosition().getY();
            }
            int count = snapshot.getEnemyUnits().size();
            snapshot.setEnemyCenter(new Position((int)(ex / count), (int)(ey / count)));
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
        if (type == UnitType.Zerg_Lurker && unit.isBurrowed() || type == UnitType.Protoss_Dark_Templar) {
            if (!enemyHasDetection) {
                cloak = 2.0;
            }
        }

        double prepPenalty = 1.0;
        if (type == UnitType.Zerg_Lurker && !unit.isBurrowed()) {
            prepPenalty = 0.3;
        }

        double rangeUpgrade = rangeUpgradeCorrection(unit, type);

        return base * hpWeight * distWeight * cloak * prepPenalty * rangeUpgrade;
    }

    private double rangeUpgradeCorrection(Unit unit, UnitType type) {
        WeaponType weapon = type.isFlyer() ? type.airWeapon() : type.groundWeapon();
        if (weapon == null || weapon == WeaponType.None) return 1.0;
        int baseRange = weapon.maxRange();
        if (baseRange <= 0) return 1.0;
        int upgradedRange = unit.getPlayer().weaponMaxRange(weapon);
        if (upgradedRange == baseRange) return 1.0;
        return Math.log(upgradedRange / 4.0 + 16.0) / Math.log(baseRange / 4.0 + 16.0);
    }

    private boolean enemyHasNearbyDetection(ObservedUnitTracker tracker, Position center, int currentFrame) {
        for (ObservedUnit ou : tracker.getLivingObservedUnits()) {
            if (!ou.getUnitType().isDetector()) continue;
            boolean visible = ou.getUnit().isVisible();
            if (!visible && !isPositionalUnit(ou.getUnitType())) {
                int framesSinceObserved = currentFrame - ou.getLastObservedFrame().getFrames();
                if (framesSinceObserved > RECENTLY_SEEN_THRESHOLD.getFrames()) continue;
            }
            Position pos = visible ? ou.getUnit().getPosition() : ou.getLastKnownLocation();
            if (pos != null && center.getDistance(pos) <= MAX_ENGAGEMENT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private boolean isPositionalUnit(UnitType type) {
        return type.isBuilding()
                || type == UnitType.Terran_Siege_Tank_Siege_Mode
                || type == UnitType.Terran_Siege_Tank_Tank_Mode
                || type == UnitType.Zerg_Lurker;
    }

    private double hpWeighting(int hp, int shields, int maxHp, int maxShields) {
        int denominator = 3 * maxHp + maxShields;
        if (denominator == 0) return 1.0;
        return (double) (3 * hp + shields) / denominator;
    }

    private double distanceWeight(double distance) {
        if (distance <= 256) return 1.0;
        if (distance <= 512) return 1.0 - 0.5 * (distance - 256) / 256;
        return 0;
    }

    private boolean isRanged(UnitType type) {
        if (type.groundWeapon() != null && type.groundWeapon().maxRange() > 32) return true;
        return type.airWeapon() != null && type.airWeapon().maxRange() > 32;
    }

    private Map<UnitSizeType, Double> sizeProportions(Squad squad, Map<Squad, Double> adjacentSquads) {
        Map<UnitSizeType, Double> proportions = new HashMap<>();
        double total = addSquadSizes(squad, 1.0, proportions);
        if (adjacentSquads != null) {
            for (Map.Entry<Squad, Double> adj : adjacentSquads.entrySet()) {
                double weight = distanceWeight(adj.getValue());
                total += addSquadSizes(adj.getKey(), weight, proportions);
            }
        }
        if (total == 0) return proportions;
        for (Map.Entry<UnitSizeType, Double> entry : proportions.entrySet()) {
            entry.setValue(entry.getValue() / total);
        }
        return proportions;
    }

    private double addSquadSizes(Squad squad, double weight, Map<UnitSizeType, Double> proportions) {
        double total = 0;
        for (Map.Entry<UnitType, Integer> entry : squad.getComposition().entrySet()) {
            UnitType type = entry.getKey();
            if (type == UnitType.Zerg_Overlord) continue;
            double s = Math.max(type.supplyRequired(), 1) * entry.getValue() * weight;
            proportions.merge(type.size(), s, Double::sum);
            total += s;
        }
        return total;
    }

    private double weightedEffectiveness(DamageType damageType, Map<UnitSizeType, Double> sizeProportions) {
        if (damageType == DamageType.Normal || sizeProportions.isEmpty()) return 1.0;
        double effectiveness = 0;
        for (Map.Entry<UnitSizeType, Double> entry : sizeProportions.entrySet()) {
            effectiveness += entry.getValue() * UnitStrength.effectiveness(damageType, entry.getKey());
        }
        return effectiveness;
    }

    private DamageType groundDamageType(UnitType type) {
        if (type == UnitType.Zerg_Sunken_Colony) return DamageType.Explosive;
        WeaponType weapon = type.groundWeapon();
        if (weapon == null || weapon == WeaponType.None) return DamageType.Normal;
        return weapon.damageType();
    }

    private DamageType airDamageType(UnitType type) {
        WeaponType weapon = type.airWeapon();
        if (weapon == null || weapon == WeaponType.None) return DamageType.Normal;
        return weapon.damageType();
    }

    @Getter
    @lombok.RequiredArgsConstructor
    public static class UnitDebugEntry {
        private final Position position;
        private final UnitType type;
        private final double strength;
        private final boolean adjacent;
        private final boolean fogOfWar;
    }

    @Getter
    @lombok.Setter
    public static class DebugSnapshot {
        private Position squadCenter;
        private Position enemyCenter;
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
