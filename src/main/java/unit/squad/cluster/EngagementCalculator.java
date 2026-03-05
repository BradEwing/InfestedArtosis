package unit.squad.cluster;

import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import unit.managed.ManagedUnit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class EngagementCalculator {

    private static final double FRONT_DISTANCE_THRESHOLD = 256.0;
    private static final double NEAR_FRONT_MIN = 224.0;
    private static final double NEARLY_ENGAGED_DISTANCE = 32.0;
    private static final double STRIDE_RELAXATION = 64.0;

    private EngagementCalculator() {}

    public static double engagementDistance(ManagedUnit friendly, EnemyCluster cluster) {
        double minDist = Double.MAX_VALUE;
        Unit friendlyUnit = friendly.getUnit();

        for (Unit enemy : cluster.getMembers()) {
            UnitType enemyType = enemy.getType();
            int weaponRange = getRelevantWeaponRange(enemyType, friendlyUnit.isFlying());
            double pixelDist = friendlyUnit.getPosition().getDistance(enemy.getPosition());
            double engDist = Math.max(0, pixelDist - weaponRange);
            if (engDist < minDist) {
                minDist = engDist;
            }
        }

        return minDist;
    }

    public static List<ManagedUnit> identifyFront(Collection<ManagedUnit> friendlies, EnemyCluster cluster) {
        List<UnitEngagement> engagements = new ArrayList<>();
        for (ManagedUnit mu : friendlies) {
            double dist = engagementDistance(mu, cluster);
            engagements.add(new UnitEngagement(mu, dist));
        }
        engagements.sort(Comparator.comparingDouble(e -> e.distance));

        List<ManagedUnit> front = new ArrayList<>();
        if (engagements.isEmpty()) return front;

        double baseDistance = engagements.get(0).distance;

        for (int i = 0; i < engagements.size(); i++) {
            UnitEngagement e = engagements.get(i);
            double allowedGap = FRONT_DISTANCE_THRESHOLD + (i * STRIDE_RELAXATION / engagements.size());
            if (e.distance - baseDistance <= allowedGap) {
                front.add(e.unit);
            } else {
                break;
            }
        }

        return front;
    }

    public static boolean isNearlyEngaged(ManagedUnit friendly, EnemyCluster cluster) {
        return engagementDistance(friendly, cluster) <= NEARLY_ENGAGED_DISTANCE;
    }

    public static boolean isInFront(ManagedUnit friendly, EnemyCluster cluster) {
        return engagementDistance(friendly, cluster) < FRONT_DISTANCE_THRESHOLD;
    }

    public static boolean isNearFront(ManagedUnit friendly, EnemyCluster cluster) {
        double dist = engagementDistance(friendly, cluster);
        return dist >= NEAR_FRONT_MIN && dist < FRONT_DISTANCE_THRESHOLD;
    }

    private static int getRelevantWeaponRange(UnitType enemyType, boolean targetIsFlying) {
        WeaponType weapon;
        if (targetIsFlying) {
            weapon = enemyType.airWeapon();
        } else {
            weapon = enemyType.groundWeapon();
        }
        if (weapon == null || weapon == WeaponType.None) return 0;
        return weapon.maxRange();
    }

    static class UnitEngagement {
        final ManagedUnit unit;
        final double distance;

        UnitEngagement(ManagedUnit unit, double distance) {
            this.unit = unit;
            this.distance = distance;
        }
    }
}
