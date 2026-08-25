package info.tracking;

import bwapi.Race;
import bwapi.TilePosition;
import bwem.Base;
import util.Distance;
import util.Time;

import java.util.HashSet;
import java.util.Set;

public abstract class ObservedStrategy {
    private static final int NATURAL_TILE_RADIUS = 10;

    private final String name;

    protected ObservedStrategy(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Returns true if the strategy is detected based on the provided detection context.
     */
    public abstract boolean isDetected(StrategyDetectionContext context);

    public Time lockAfter() {
        return new Time(59, 59);
    }

    public Race getRace() {
        return Race.Unknown;
    }

    /**
     * Tiles of our main base plus a manhattan radius around our inferred natural.
     * Use for arrival-based detection of enemy units at our bases.
     */
    protected Set<TilePosition> ourBaseTiles(StrategyDetectionContext context) {
        Set<TilePosition> tiles = new HashSet<>(context.getGameMap().getMainBaseTiles());
        Base natural = context.getBaseData().getInferredNaturalBase();
        if (natural != null) {
            tiles.addAll(Distance.tilesWithinManhattanDistance(natural.getLocation(), NATURAL_TILE_RADIUS));
        }
        return tiles;
    }
}
