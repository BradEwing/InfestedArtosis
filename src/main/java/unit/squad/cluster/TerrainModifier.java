package unit.squad.cluster;

import bwapi.Game;
import bwapi.Position;
import bwapi.WalkPosition;
import bwem.BWEM;
import bwem.ChokePoint;
import bwem.CPPath;
import lombok.Getter;

public final class TerrainModifier {

    private static final double HIGH_GROUND_MULTIPLIER = 1.5;
    private static final double LOW_GROUND_MULTIPLIER = 2.0;
    private static final double CHOKE_PENALTY_MAX = 0.5;
    private static final double NARROW_CHOKE_WIDTH = 128.0;
    private static final double MEDIUM_CHOKE_WIDTH = 256.0;

    private TerrainModifier() {}

    public static ModifiedSupply applyModifiers(
            SupplyBreakdown frontSupply,
            SupplyBreakdown enemySupply,
            Position frontCentroid,
            Position enemyVanguard,
            Game game,
            BWEM bwem) {

        SupplyBreakdown adjustedFront = frontSupply;
        SupplyBreakdown adjustedEnemy = enemySupply;

        int frontHeight = game.getGroundHeight(frontCentroid.toTilePosition());
        int enemyHeight = game.getGroundHeight(enemyVanguard.toTilePosition());

        if (frontHeight != enemyHeight) {
            double frontRanged = rangedFraction(frontSupply);
            double enemyRanged = rangedFraction(enemySupply);
            double fraction = frontRanged * enemyRanged;
            if (frontHeight > enemyHeight) {
                adjustedFront = augmentSupply(adjustedFront, HIGH_GROUND_MULTIPLIER, fraction);
            } else {
                adjustedEnemy = augmentSupply(adjustedEnemy, LOW_GROUND_MULTIPLIER, fraction);
            }
        }

        double chokePenalty = calculateChokePenalty(frontCentroid, enemyVanguard, bwem);
        if (chokePenalty > 0) {
            double rangedFraction = rangedFraction(frontSupply);
            double effectivePenalty = chokePenalty * (1.0 - rangedFraction);
            adjustedFront = adjustedFront.scale(1.0 - effectivePenalty);
        }

        return new ModifiedSupply(adjustedFront, adjustedEnemy);
    }

    private static double calculateChokePenalty(Position from, Position to, BWEM bwem) {
        try {
            CPPath path = bwem.getMap().getPath(from, to);
            if (path.isEmpty()) return 0;

            for (ChokePoint cp : path) {
                WalkPosition end1 = cp.getNodePosition(ChokePoint.Node.END1);
                WalkPosition end2 = cp.getNodePosition(ChokePoint.Node.END2);
                double width = end1.toPosition().getDistance(end2.toPosition());
                if (width < NARROW_CHOKE_WIDTH) {
                    return CHOKE_PENALTY_MAX;
                } else if (width < MEDIUM_CHOKE_WIDTH) {
                    return CHOKE_PENALTY_MAX * 0.5;
                }
            }
        } catch (Exception e) {
        }
        return 0;
    }

    private static SupplyBreakdown augmentSupply(SupplyBreakdown supply, double factor, double fraction) {
        double scaledFactor = (1.0 - fraction) + fraction * factor;
        return supply.scale(scaledFactor);
    }

    private static double rangedFraction(SupplyBreakdown supply) {
        double total = supply.total();
        if (total == 0) return 0;
        return supply.getRangedGroundSupply() / total;
    }

    @Getter
    public static class ModifiedSupply {
        private final SupplyBreakdown frontSupply;
        private final SupplyBreakdown enemySupply;

        public ModifiedSupply(SupplyBreakdown frontSupply, SupplyBreakdown enemySupply) {
            this.frontSupply = frontSupply;
            this.enemySupply = enemySupply;
        }
    }
}
