package info.tracking.terran;

import bwapi.TilePosition;
import info.map.BaseArea;
import info.tracking.StrategyDetectionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Detects a Bunker held at the enemy natural: a living Bunker in the enemy natural's area, see
 * {@link StrategyDetectionContext#enemyNaturalArea}, that does not belong to the enemy main, see
 * {@link TerranBunker}. It holds while any such Bunker is alive or last seen alive, and stops holding once every one
 * is seen dead, so a Bunker the enemy rebuilds holds it again. StrategyTracker keeps the strategy in its detected set
 * for the game and re-reads {@link #isDetected} every frame for the hold.
 */
public class BunkerNatural extends TerranBaseStrategy {

    public static final String NAME = "BunkerNatural";

    private List<TilePosition> held = new ArrayList<>();

    public BunkerNatural() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        BaseArea enemyNatural = context.enemyNaturalArea(TerranBunker.NATURAL_PROXIMITY_TILE_RADIUS,
                TerranBunker.NATURAL_AREA_TILE_RADIUS);
        if (enemyNatural == null) {
            held = new ArrayList<>();
            return false;
        }
        held = heldBunkers(TerranBunker.livingBunkerTiles(context), enemyNatural::contains,
                TerranBunker.atEnemyMain(context));
        return !held.isEmpty();
    }

    @Override
    public String getDetectionLabel() {
        return TerranBunker.label(getName(), held);
    }

    /**
     * The living Bunkers that stand in the enemy natural and not at the enemy main.
     */
    static List<TilePosition> heldBunkers(Collection<TilePosition> living, Predicate<TilePosition> inNatural,
                                          Predicate<TilePosition> atEnemyMain) {
        return TerranBunker.passing(living, inNatural.and(atEnemyMain.negate()));
    }
}
