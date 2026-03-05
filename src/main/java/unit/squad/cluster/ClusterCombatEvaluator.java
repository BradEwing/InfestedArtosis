package unit.squad.cluster;

import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import bwem.BWEM;
import info.GameState;
import info.tracking.ObservedUnit;
import lombok.Getter;
import unit.managed.ManagedUnit;
import unit.squad.CombatSimulator;
import unit.squad.Squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClusterCombatEvaluator implements CombatSimulator {

    private static final double WIN_THRESHOLD = 1.0;
    private static final double WIN_THRESHOLD_ENGAGED = 0.8;
    private static final double CLUSTER_RELEVANCE_RADIUS = 512.0;

    private List<EnemyCluster> clusters = new ArrayList<>();
    private Map<ManagedUnit, UnitDisposition> lastDispositions = new HashMap<>();
    private List<ClusterEvaluation> lastEvaluations = new ArrayList<>();

    @Override
    public CombatResult evaluate(Squad squad, Set<ManagedUnit> reinforcements, GameState gameState) {
        if (gameState.isAllIn()) {
            setAllAdvance(squad);
            return CombatResult.ENGAGE;
        }

        Game game = gameState.getGame();
        BWEM bwem = gameState.getBwem();

        lastDispositions.clear();
        lastEvaluations.clear();

        List<EnemyCluster> relevantClusters = findRelevantClusters(squad);
        if (relevantClusters.isEmpty()) {
            setAllAdvance(squad);
            return CombatResult.ENGAGE;
        }

        Set<ManagedUnit> allFriendlies = new HashSet<>(squad.getMembers());
        allFriendlies.addAll(reinforcements);

        boolean anyLosing = false;
        boolean anyWinning = false;

        for (EnemyCluster cluster : relevantClusters) {
            List<ManagedUnit> front = EngagementCalculator.identifyFront(allFriendlies, cluster);
            if (front.isEmpty()) continue;

            boolean hasCloakedThreat = hasCloakedWithoutDetection(cluster, squad, gameState);

            SupplyBreakdown frontSupply;
            if (hasCloakedThreat) {
                frontSupply = new SupplyBreakdown(0, 0, 0, 0);
            } else {
                frontSupply = SupplyCalculator.calculateFriendly(front);
            }
            SupplyBreakdown enemySupply = cluster.getSupply();

            Position frontCentroid = computeCentroid(front);
            Position enemyVanguard = findVanguard(cluster, frontCentroid);

            TerrainModifier.ModifiedSupply modified = TerrainModifier.applyModifiers(
                    frontSupply, enemySupply, frontCentroid, enemyVanguard, game, bwem);

            boolean nearlyEngaged = isAnyNearlyEngaged(squad.getMembers(), cluster);
            double threshold = nearlyEngaged ? WIN_THRESHOLD_ENGAGED : WIN_THRESHOLD;
            double effectiveFront;
            double effectiveEnemy;
            if (isAirSquad(squad)) {
                effectiveFront = modified.getFrontSupply().getAirToGroundSupply()
                        + modified.getFrontSupply().getAirToAirSupply();
                effectiveEnemy = modified.getEnemySupply().getAntiAirSupply();
            } else {
                effectiveFront = modified.getFrontSupply().combatSupply();
                effectiveEnemy = modified.getEnemySupply().combatSupply();
            }
            boolean expectWin = effectiveEnemy == 0 || effectiveFront > threshold * effectiveEnemy;

            ClusterEvaluation eval = new ClusterEvaluation(cluster, front, expectWin,
                    effectiveFront, effectiveEnemy);
            lastEvaluations.add(eval);

            if (expectWin) {
                anyWinning = true;
            } else {
                anyLosing = true;
            }

            assignDispositions(squad.getMembers(), cluster, front, expectWin);
        }

        if (anyLosing && !anyWinning) {
            return CombatResult.RETREAT;
        }
        return CombatResult.ENGAGE;
    }

    public Map<ManagedUnit, UnitDisposition> getLastDispositions() {
        return lastDispositions;
    }

    public List<ClusterEvaluation> getLastEvaluations() {
        return lastEvaluations;
    }

    public List<EnemyCluster> getClusters() {
        return clusters;
    }

    public void setClusters(List<EnemyCluster> clusters) {
        this.clusters = clusters;
    }

    public Set<Unit> getLosingClusterEnemies() {
        Set<Unit> result = new HashSet<>();
        for (ClusterEvaluation eval : lastEvaluations) {
            if (!eval.isExpectWin()) {
                result.addAll(eval.getCluster().getUnits());
            }
        }
        return result;
    }

    private void assignDispositions(Set<ManagedUnit> squadMembers, EnemyCluster cluster,
                                    List<ManagedUnit> front, boolean expectWin) {
        Set<ManagedUnit> frontSet = new HashSet<>(front);

        for (ManagedUnit mu : squadMembers) {
            if (lastDispositions.containsKey(mu) && lastDispositions.get(mu) == UnitDisposition.RETREAT) {
                continue;
            }

            if (expectWin) {
                lastDispositions.put(mu, UnitDisposition.ADVANCE);
                continue;
            }

            if (frontSet.contains(mu)) {
                boolean nearFront = EngagementCalculator.isNearFront(mu, cluster);
                if (nearFront) {
                    lastDispositions.put(mu, UnitDisposition.HOLD);
                } else if (EngagementCalculator.isInFront(mu, cluster)) {
                    if (shouldPushThrough(mu, cluster)) {
                        lastDispositions.put(mu, UnitDisposition.ADVANCE);
                    } else {
                        lastDispositions.put(mu, UnitDisposition.RETREAT);
                    }
                } else {
                    lastDispositions.putIfAbsent(mu, UnitDisposition.HOLD);
                }
            } else {
                lastDispositions.putIfAbsent(mu, UnitDisposition.HOLD);
            }
        }
    }

    private boolean shouldPushThrough(ManagedUnit friendly, EnemyCluster cluster) {
        Unit unit = friendly.getUnit();
        UnitType type = unit.getType();
        WeaponType groundWeapon = type.groundWeapon();
        if (groundWeapon == null || groundWeapon == WeaponType.None) return false;
        boolean isMelee = groundWeapon.maxRange() <= 32;

        if (isMelee) {
            if (!unit.isAttacking()) return false;
            for (ObservedUnit ou : cluster.getMembers()) {
                UnitType enemyType = ou.getUnitType();
                WeaponType enemyWeapon = enemyType.groundWeapon();
                if (enemyWeapon != null && enemyWeapon != WeaponType.None && enemyWeapon.maxRange() > 32) {
                    if (enemyType.topSpeed() < type.topSpeed()) {
                        return true;
                    }
                }
            }
        } else {
            double engDist = EngagementCalculator.engagementDistance(friendly, cluster);
            if (engDist == 0) {
                for (ObservedUnit ou : cluster.getMembers()) {
                    if (ou.getUnitType() == UnitType.Terran_Siege_Tank_Siege_Mode) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasCloakedWithoutDetection(EnemyCluster cluster, Squad squad, GameState gameState) {
        for (ObservedUnit ou : cluster.getMembers()) {
            Unit enemy = ou.getUnit();
            if (enemy.isVisible() && !enemy.isDetected() && (enemy.isCloaked() || enemy.isBurrowed())) {
                boolean hasDetection = false;
                for (ManagedUnit mu : squad.getMembers()) {
                    if (mu.getUnit().getType() == UnitType.Zerg_Overlord
                            && mu.getUnit().getPosition().getDistance(ou.getEffectivePosition()) < 384) {
                        hasDetection = true;
                        break;
                    }
                }
                if (!hasDetection) return true;
            }
        }
        return false;
    }

    private void setAllAdvance(Squad squad) {
        lastDispositions.clear();
        for (ManagedUnit mu : squad.getMembers()) {
            lastDispositions.put(mu, UnitDisposition.ADVANCE);
        }
    }

    private List<EnemyCluster> findRelevantClusters(Squad squad) {
        List<EnemyCluster> relevant = new ArrayList<>();
        Position center = squad.getCenter();
        if (center == null) return relevant;

        for (EnemyCluster cluster : clusters) {
            if (cluster.getVisibleMembers().isEmpty()) continue;
            if (center.getDistance(cluster.getCentroid()) <= CLUSTER_RELEVANCE_RADIUS + cluster.getRadius()) {
                relevant.add(cluster);
            }
        }
        return relevant;
    }

    private boolean isAnyNearlyEngaged(Set<ManagedUnit> units, EnemyCluster cluster) {
        for (ManagedUnit mu : units) {
            if (EngagementCalculator.isNearlyEngaged(mu, cluster)) {
                return true;
            }
        }
        return false;
    }

    private Position computeCentroid(List<ManagedUnit> units) {
        if (units.isEmpty()) return new Position(0, 0);
        int x = 0, y = 0;
        for (ManagedUnit mu : units) {
            Position p = mu.getUnit().getPosition();
            x += p.getX();
            y += p.getY();
        }
        return new Position(x / units.size(), y / units.size());
    }

    private boolean isAirSquad(Squad squad) {
        UnitType type = squad.getType();
        return type != null && type.isFlyer();
    }

    private Position findVanguard(EnemyCluster cluster, Position frontCentroid) {
        if (cluster.getVisibleMembers().isEmpty()) {
            return cluster.getCentroid();
        }
        Position closest = cluster.getCentroid();
        double minDist = Double.MAX_VALUE;
        for (ObservedUnit ou : cluster.getVisibleMembers()) {
            double dist = frontCentroid.getDistance(ou.getEffectivePosition());
            if (dist < minDist) {
                minDist = dist;
                closest = ou.getEffectivePosition();
            }
        }
        return closest;
    }

    @Getter
    public static class ClusterEvaluation {
        private final EnemyCluster cluster;
        private final List<ManagedUnit> front;
        private final boolean expectWin;
        private final double frontSupply;
        private final double enemySupply;

        public ClusterEvaluation(EnemyCluster cluster, List<ManagedUnit> front, boolean expectWin,
                                 double frontSupply, double enemySupply) {
            this.cluster = cluster;
            this.front = front;
            this.expectWin = expectWin;
            this.frontSupply = frontSupply;
            this.enemySupply = enemySupply;
        }
    }
}
