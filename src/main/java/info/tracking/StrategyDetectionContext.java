package info.tracking;

import bwapi.TilePosition;
import bwem.Base;
import info.BaseData;
import info.map.GameMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.Distance;
import util.Time;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class StrategyDetectionContext {
    @Getter
    private final ObservedUnitTracker tracker;
    @Getter
    private final Time time;
    @Getter
    private final BaseData baseData;
    @Getter
    private final GameMap gameMap;

    private final Map<Integer, Set<TilePosition>> ourBaseTilesByNaturalRadius = new HashMap<>();

    /**
     * Tiles of our main base plus a manhattan radius around our inferred natural.
     * Cached per context instance so detectors sharing a frame do not recompute it.
     */
    public Set<TilePosition> ourBaseTiles(int naturalTileRadius) {
        return ourBaseTilesByNaturalRadius.computeIfAbsent(naturalTileRadius, this::computeOurBaseTiles);
    }

    private Set<TilePosition> computeOurBaseTiles(int naturalTileRadius) {
        Set<TilePosition> tiles = new HashSet<>(gameMap.getMainBaseTiles());
        Base natural = baseData.getInferredNaturalBase();
        if (natural != null) {
            tiles.addAll(Distance.tilesWithinManhattanDistance(natural.getLocation(), naturalTileRadius));
        }
        return tiles;
    }
}
