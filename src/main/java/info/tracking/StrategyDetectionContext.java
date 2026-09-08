package info.tracking;

import bwapi.TilePosition;
import bwem.BWMap;
import bwem.Base;
import info.BaseData;
import info.map.BaseArea;
import info.map.GameMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.Time;

import java.util.HashMap;
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
    private final BWMap bwMap;

    private final Map<Integer, Set<TilePosition>> ourBaseTilesByNaturalRadius = new HashMap<>();
    private final Map<Integer, BaseArea> enemyNaturalAreaByRadius = new HashMap<>();

    /**
     * Tiles of our main base plus a manhattan radius around our inferred natural.
     * Cached per context instance so detectors sharing a frame do not recompute it.
     */
    public Set<TilePosition> ourBaseTiles(int naturalTileRadius) {
        return ourBaseTilesByNaturalRadius.computeIfAbsent(naturalTileRadius, this::computeOurBaseTiles);
    }

    /**
     * Ground belonging to the inferred enemy natural, widened by a manhattan radius around its depot tile
     * and around the chokepoints of its area. Null while the enemy natural is unknown.
     * Cached per context instance so detectors sharing a frame do not recompute it.
     */
    public BaseArea enemyNaturalArea(int manhattanRadius) {
        Base enemyNatural = baseData.getEnemyNaturalBase();
        if (enemyNatural == null) {
            return null;
        }
        return enemyNaturalAreaByRadius.computeIfAbsent(manhattanRadius, radius -> BaseArea.from(enemyNatural, bwMap, radius));
    }

    private Set<TilePosition> computeOurBaseTiles(int naturalTileRadius) {
        return baseData.ourBaseTiles(gameMap, naturalTileRadius);
    }
}
