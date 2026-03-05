package unit.squad.cluster;

import bwapi.Game;
import bwapi.Position;
import bwapi.WalkPosition;
import bwem.BWEM;
import bwem.ChokePoint;
import bwem.CPPath;

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

        if (frontHeight > enemyHeight) {
            adjustedFront = adjustedFront.scale(HIGH_GROUND_MULTIPLIER);
        } else if (frontHeight < enemyHeight) {
            adjustedEnemy = adjustedEnemy.scale(LOW_GROUND_MULTIPLIER);
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
            // BWEM path errors are non-fatal
        }
        return 0;
    }

    private static double rangedFraction(SupplyBreakdown supply) {
        double total = supply.total();
        if (total == 0) return 0;
        return supply.getRangedGroundSupply() / total;
    }

    public static class ModifiedSupply {
        private final SupplyBreakdown frontSupply;
        private final SupplyBreakdown enemySupply;

        public ModifiedSupply(SupplyBreakdown frontSupply, SupplyBreakdown enemySupply) {
            this.frontSupply = frontSupply;
            this.enemySupply = enemySupply;
        }

        public SupplyBreakdown getFrontSupply() { return frontSupply; }
        public SupplyBreakdown getEnemySupply() { return enemySupply; }
    }
}
