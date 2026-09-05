package unit.squad.horizon;

import bwapi.DamageType;
import bwapi.Position;
import bwapi.Race;
import bwapi.Unit;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import bwapi.WeaponType;
import info.GameState;
import info.TechProgression;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import lombok.Getter;
import unit.managed.ManagedUnit;
import unit.squad.CombatSimulator;
import unit.squad.Squad;
import util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combat simulator inspired by McRave's Horizon.
 * https://github.com/Cmccrave/McRave/tree/bfbce3c74d240f4a2cbfe6767c8172af842049be/Source/Horizon
 */
public class HorizonCombatSimulator implements CombatSimulator {

    private static final double MAX_ENGAGEMENT_RADIUS = 320;
    private static final double NEARBY_THREAT_RADIUS = 512;
    private static final double APPROACH_BUFFER = 64;
    private static final double WORKER_STRENGTH_DIVISOR = 10.0;
    private static final double HEIGHT_BONUS = 1.15;
    private static final Time RECENTLY_SEEN_THRESHOLD = new Time(0, 5);
    private static final Time BUILDING_SEEN_THRESHOLD = new Time(0, 45);
    private static final double DEFAULT_ENGAGE_THRESHOLD = 1.0;
    private static final double DEFAULT_RETREAT_THRESHOLD = 0.7;
    private static final double MIN_ENEMY_STRENGTH = 0.01;
    private static final double SPEED_UPGRADE_PENALTY = 0.75;
    private static final int BUNKER_TRUST_FRAMES = 48;
    private static final int BUNKER_DECAY_FRAMES = 72;
    private static final int BUNKER_MAX_GARRISON = 4;
    private static final double STATIC_DEFENSE_COVER_BUFFER = 64;
    private static final Set<UnitType> OWN_STATIC_DEFENSE =
            EnumSet.of(UnitType.Zerg_Sunken_Colony, UnitType.Zerg_Spore_Colony);

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
        TechProgression techProgression = gameState.getTechProgression();
        DebugSnapshot snapshot = new DebugSnapshot();
        snapshot.setCapturedFrame(currentFrame);
        snapshot.setSquadCenter(squadCenter);

        double friendlyGroundStr = 0;
        double friendlyAirStr = 0;

        for (ManagedUnit mu : squad.getMembers()) {
            if (mu.getUnitType() == UnitType.Zerg_Overlord) continue;
            double str = computeFriendlyStrength(mu, squadCenter, enemyHasDetection, techProgression);
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
                    double str = computeFriendlyStrength(mu, squadCenter, enemyHasDetection, techProgression) * weight;
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

        List<Position> engagedGroundEnemies = new ArrayList<>();
        List<Position> coveredGroundThreats = new ArrayList<>();
        List<Position> coveredAirThreats = new ArrayList<>();

        for (ObservedUnit ou : tracker.getLivingObservedUnits()) {
            UnitType type = ou.getUnitType();
            boolean visible = ou.getUnit().isVisible();
            if (!visible) {
                int framesSinceObserved = currentFrame - ou.getLastObservedFrame().getFrames();
                if (framesSinceObserved > freshnessThreshold(type)) continue;
            }

            Position pos = visible ? ou.getUnit().getPosition() : ou.getLastKnownLocation();
            if (pos == null) continue;
            double dist = squadCenter.getDistance(pos);
            double radius = engagementRadius(type);
            if (dist > radius) {
                if (isThreatBeyondRadius(type, dist, radius)) {
                    snapshot.setThreatBeyondRadius(true);
                }
                continue;
            }

            if (!type.isBuilding() && !type.isWorker()) {
                if (!type.isFlyer()) {
                    engagedGroundEnemies.add(pos);
                }
                if (visible && type.canAttack()) {
                    if (type.isFlyer()) {
                        coveredAirThreats.add(pos);
                    } else {
                        coveredGroundThreats.add(pos);
                    }
                }
            }

            if (type.isBuilding() && !ou.isCompleted()) continue;
            double hpWeight = hpWeighting(ou.getLastKnownHitPoints(), ou.getLastKnownShields(),
                    type.maxHitPoints(), type.maxShields());
            double distWeight = type.isBuilding() ? 1.0 : distanceWeight(dist);
            double heightMod = 1.0;
            if (!type.isFlyer() && isRanged(type) && ou.getLastKnownGroundHeight() > 0) {
                heightMod = HEIGHT_BONUS;
            }

            double groundBase = UnitStrength.groundToGround(type) + UnitStrength.airToGround(type);
            double antiAirBase = UnitStrength.antiAirStrength(type);
            if (type == UnitType.Terran_Bunker) {
                double garrisonMod = bunkerGarrisonModifier(ou, currentFrame);
                groundBase *= garrisonMod;
                antiAirBase *= garrisonMod;
            }
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

        StaticDefenseSupport ownStaticDefense = evaluateOwnStaticDefense(gameState, squadCenter,
                engagedGroundEnemies, coveredGroundThreats, coveredAirThreats, !airSquad, snapshot);
        friendlyGroundStr += ownStaticDefense.strength;

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

        double relevantEnemyStr = airSquad ? enemyAntiAirStr : enemyGroundStr;
        boolean enemyMeasured = relevantEnemyStr > MIN_ENEMY_STRENGTH;
        snapshot.setEnemyMeasured(enemyMeasured);
        double overallRatio = 0;
        if (!enemyMeasured) {
            snapshot.setGroundRatio(0);
            snapshot.setCombinedRatio(0);
        } else if (airSquad) {
            overallRatio = friendlyAirStr / enemyAntiAirStr;
            snapshot.setGroundRatio(0);
            snapshot.setCombinedRatio(overallRatio);
        } else {
            double groundRatio = friendlyGroundStr / enemyGroundStr;
            double totalFriendly = friendlyGroundStr + friendlyAirStr;
            double totalEnemy = enemyGroundStr + enemyAntiAirStr;
            double combinedRatio = totalFriendly / totalEnemy;
            overallRatio = Math.max(groundRatio, combinedRatio);
            snapshot.setGroundRatio(groundRatio);
            snapshot.setCombinedRatio(combinedRatio);
        }

        snapshot.setFriendlyTotal(friendlyGroundStr + friendlyAirStr);
        snapshot.setEnemyTotal(airSquad ? enemyAntiAirStr : enemyGroundStr);
        snapshot.setOverallRatio(overallRatio);

        double engageThresh = engageThreshold(gameState.getOpponentRace());
        double retreatThresh = retreatThreshold(gameState.getOpponentRace());
        if (ownStaticDefense.coversThreat) {
            engageThresh = Math.min(engageThresh, DEFAULT_ENGAGE_THRESHOLD);
        }

        CombatResult result = selectResult(friendlyGroundStr, friendlyAirStr, enemyGroundStr,
                enemyAntiAirStr, airSquad, engageThresh);

        snapshot.setEngageThreshold(engageThresh);
        snapshot.setRetreatThreshold(retreatThresh);
        snapshot.setStaticDefenseCover(ownStaticDefense.coversThreat);
        snapshot.setResult(result);
        lastSnapshots.put(squad.getId(), snapshot);

        return result;
    }

    static CombatResult selectResult(double friendlyGroundStr, double friendlyAirStr,
                                     double enemyGroundStr, double enemyAntiAirStr,
                                     boolean airSquad, double engageThresh) {
        double relevantEnemyStr = airSquad ? enemyAntiAirStr : enemyGroundStr;
        double ratio;
        if (airSquad) {
            ratio = friendlyAirStr / Math.max(enemyAntiAirStr, MIN_ENEMY_STRENGTH);
        } else {
            double groundRatio = friendlyGroundStr / Math.max(enemyGroundStr, MIN_ENEMY_STRENGTH);
            double totalFriendly = friendlyGroundStr + friendlyAirStr;
            double totalEnemy = enemyGroundStr + enemyAntiAirStr;
            double combinedRatio = totalFriendly / Math.max(totalEnemy, MIN_ENEMY_STRENGTH);
            ratio = Math.max(groundRatio, combinedRatio);
        }
        if (ratio < engageThresh) return CombatResult.RETREAT;
        return relevantEnemyStr > MIN_ENEMY_STRENGTH ? CombatResult.ENGAGE : CombatResult.ADVANCE;
    }

    /**
     * Whether a fresh, attack-capable, non-worker enemy sits outside its type's engagement radius
     * but close enough to matter, i.e. within {@link #NEARBY_THREAT_RADIUS}.
     *
     * @param type enemy unit type
     * @param distance distance from the squad center to the enemy
     * @param engagementRadius the type's own engagement radius, already excluded by the caller
     * @return true if the enemy is a nearby but unmeasured threat
     */
    static boolean isThreatBeyondRadius(UnitType type, double distance, double engagementRadius) {
        if (distance <= engagementRadius || distance > NEARBY_THREAT_RADIUS) return false;
        return type.canAttack() && !type.isWorker();
    }

    private double computeFriendlyStrength(ManagedUnit mu, Position engagementCenter, boolean enemyHasDetection, TechProgression techProgression) {
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

        double speedPenalty = 1.0;
        if (type == UnitType.Zerg_Zergling && !techProgression.isMetabolicBoost()
                || type == UnitType.Zerg_Hydralisk && !techProgression.isMuscularAugments()) {
            speedPenalty = SPEED_UPGRADE_PENALTY;
        }

        double attackUpgrade = attackUpgradeCorrection(unit, type);
        double adrenalGlands = adrenalGlandsCorrection(unit, type, techProgression);
        double armorUpgrade = armorUpgradeCorrection(unit, type);

        return base * hpWeight * distWeight * cloak * prepPenalty * rangeUpgrade * speedPenalty
                * attackUpgrade * adrenalGlands * armorUpgrade;
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

    private double attackUpgradeCorrection(Unit unit, UnitType type) {
        WeaponType weapon = type.isFlyer() ? type.airWeapon() : type.groundWeapon();
        if (weapon == null || weapon == WeaponType.None) return 1.0;
        int baseDamage = weapon.damageAmount() * weapon.damageFactor();
        if (baseDamage <= 0) return 1.0;
        int upgradedDamage = unit.getPlayer().damage(weapon);
        if (upgradedDamage == baseDamage) return 1.0;
        return (double) upgradedDamage / baseDamage;
    }

    private double adrenalGlandsCorrection(Unit unit, UnitType type, TechProgression techProgression) {
        if (type != UnitType.Zerg_Zergling || !techProgression.isAdrenalGlands()) return 1.0;
        int baseCooldown = type.groundWeapon().damageCooldown();
        int upgradedCooldown = unit.getPlayer().weaponDamageCooldown(type);
        if (upgradedCooldown >= baseCooldown || upgradedCooldown <= 0) return 1.0;
        return (double) baseCooldown / upgradedCooldown;
    }

    private double armorUpgradeCorrection(Unit unit, UnitType type) {
        int baseArmor = type.armor();
        int currentArmor = unit.getPlayer().armor(type);
        int armorBonus = currentArmor - baseArmor;
        if (armorBonus <= 0) return 1.0;
        return 1.0 + armorBonus * 0.06;
    }

    private boolean enemyHasNearbyDetection(ObservedUnitTracker tracker, Position center, int currentFrame) {
        for (ObservedUnit ou : tracker.getLivingObservedUnits()) {
            if (!ou.getUnitType().isDetector()) continue;
            boolean visible = ou.getUnit().isVisible();
            if (!visible) {
                int framesSinceObserved = currentFrame - ou.getLastObservedFrame().getFrames();
                if (framesSinceObserved > freshnessThreshold(ou.getUnitType())) continue;
            }
            Position pos = visible ? ou.getUnit().getPosition() : ou.getLastKnownLocation();
            if (pos != null && center.getDistance(pos) <= engagementRadius(ou.getUnitType())) {
                return true;
            }
        }
        return false;
    }

    private int freshnessThreshold(UnitType type) {
        if (type.isBuilding()) return BUILDING_SEEN_THRESHOLD.getFrames();
        if (isPositionalUnit(type)) return Integer.MAX_VALUE;
        return RECENTLY_SEEN_THRESHOLD.getFrames();
    }

    private boolean isPositionalUnit(UnitType type) {
        return type.isBuilding()
                || type == UnitType.Terran_Siege_Tank_Siege_Mode
                || type == UnitType.Zerg_Lurker;
    }

    /**
     * Scans our own completed static defense once, producing both the ground strength contribution
     * and whether any colony currently covers a threat it is able to shoot.
     *
     * @param groundDomain whether the ground domain feeds this squad's ratio, gating both the
     *                     sunken strength term and ground-based coverage
     */
    private StaticDefenseSupport evaluateOwnStaticDefense(GameState gameState, Position squadCenter,
                                                          List<Position> engagedGroundEnemies,
                                                          List<Position> coveredGroundThreats,
                                                          List<Position> coveredAirThreats,
                                                          boolean groundDomain, DebugSnapshot snapshot) {
        StaticDefenseSupport support = new StaticDefenseSupport();
        List<Position> groundCover = groundDomain ? coveredGroundThreats : Collections.<Position>emptyList();
        if (engagedGroundEnemies.isEmpty() && groundCover.isEmpty() && coveredAirThreats.isEmpty()) return support;

        for (Unit unit : gameState.getSelf().getUnits()) {
            UnitType type = unit.getType();
            if (!OWN_STATIC_DEFENSE.contains(type)) continue;
            if (!unit.isCompleted()) continue;

            Position colonyPosition = unit.getPosition();
            if (colonyPosition == null) continue;
            double radius = engagementRadius(type);
            if (squadCenter.getDistance(colonyPosition) > radius) continue;

            boolean covers = coversThreat(type, colonyPosition, groundCover, coveredAirThreats);
            if (covers) {
                support.coversThreat = true;
            }

            double str = 0;
            if (groundDomain && type == UnitType.Zerg_Sunken_Colony
                    && anyWithinRange(engagedGroundEnemies, colonyPosition, radius)) {
                double hpWeight = hpWeighting(unit.getHitPoints(), 0, type.maxHitPoints(), 0);
                str = UnitStrength.groundToGround(type) * hpWeight;
                support.strength += str;
            }

            if (covers || str > 0) {
                snapshot.getFriendlyUnits().add(new UnitDebugEntry(colonyPosition, type, str, true, false));
            }
        }

        return support;
    }

    /**
     * Whether a static defense structure is in weapon range of a threat it can actually shoot.
     * The ground and air threat lists are matched against the matching weapon, so a structure
     * without a weapon in that domain never covers threats there: a Sunken Colony has no air
     * weapon and therefore cannot be triggered by an air threat, and a Spore Colony likewise
     * cannot be triggered by a ground threat.
     */
    static boolean coversThreat(UnitType defenseType, Position defensePosition,
                                List<Position> groundThreats, List<Position> airThreats) {
        return weaponCovers(defenseType.groundWeapon(), defensePosition, groundThreats)
                || weaponCovers(defenseType.airWeapon(), defensePosition, airThreats);
    }

    private static boolean weaponCovers(WeaponType weapon, Position origin, List<Position> threats) {
        if (weapon == null || weapon == WeaponType.None) return false;
        return anyWithinRange(threats, origin, weapon.maxRange() + STATIC_DEFENSE_COVER_BUFFER);
    }

    private static boolean anyWithinRange(List<Position> positions, Position origin, double range) {
        for (Position pos : positions) {
            if (pos.getDistance(origin) <= range) return true;
        }
        return false;
    }

    private static final class StaticDefenseSupport {
        private double strength;
        private boolean coversThreat;
    }

    private double engagementRadius(UnitType type) {
        if (!isPositionalUnit(type)) return MAX_ENGAGEMENT_RADIUS;
        int groundRange = type.groundWeapon() != null && type.groundWeapon() != WeaponType.None
                ? type.groundWeapon().maxRange() : 0;
        int airRange = type.airWeapon() != null && type.airWeapon() != WeaponType.None
                ? type.airWeapon().maxRange() : 0;
        return Math.max(MAX_ENGAGEMENT_RADIUS, Math.max(groundRange, airRange) + APPROACH_BUFFER);
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

    private double bunkerGarrisonModifier(ObservedUnit ou, int currentFrame) {
        int loadedCount = ou.getLastKnownLoadedCount();
        if (loadedCount < 0) return 1.0;
        double baseModifier = (double) loadedCount / BUNKER_MAX_GARRISON;
        int elapsed = currentFrame - ou.getLastLoadedCheckFrame();
        if (elapsed <= BUNKER_TRUST_FRAMES) return baseModifier;
        if (elapsed >= BUNKER_TRUST_FRAMES + BUNKER_DECAY_FRAMES) return 1.0;
        double decayProgress = (double) (elapsed - BUNKER_TRUST_FRAMES) / BUNKER_DECAY_FRAMES;
        return baseModifier + (1.0 - baseModifier) * decayProgress;
    }

    private double engageThreshold(Race opponentRace) {
        switch (opponentRace) {
            case Terran:  return 1.4;
            case Protoss: return 0.9;
            case Zerg:    return 1.3;
            default:      return DEFAULT_ENGAGE_THRESHOLD;
        }
    }

    private double retreatThreshold(Race opponentRace) {
        switch (opponentRace) {
            case Terran:  return 1.0;
            case Protoss: return 0.5;
            case Zerg:    return 0.7;
            default:      return DEFAULT_RETREAT_THRESHOLD;
        }
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
        private int capturedFrame;
        private Position squadCenter;
        private Position enemyCenter;
        private final List<UnitDebugEntry> friendlyUnits = new ArrayList<>();
        private final List<UnitDebugEntry> enemyUnits = new ArrayList<>();
        private double friendlyTotal;
        private double enemyTotal;
        private double groundRatio;
        private double combinedRatio;
        private double overallRatio;
        private double engageThreshold;
        private double retreatThreshold;
        private boolean staticDefenseCover;
        private CombatResult result;
        private boolean enemyMeasured;
        private boolean threatBeyondRadius;
    }
}
