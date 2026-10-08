package info.tracking.terran;

import bwapi.TilePosition;
import info.tracking.StrategyDetectionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Detects a Bunker held at the enemy main: a living Bunker in the enemy main's BWEM Area or at one of its
 * chokepoints, see {@link TerranBunker}. It holds while any such Bunker is alive or last seen alive, and stops
 * holding once every one is seen dead, so a Bunker the enemy rebuilds holds it again. StrategyTracker keeps the
 * strategy in its detected set for the game and re-reads {@link #isDetected} every frame for the hold.
 */
public class BunkerMain extends TerranBaseStrategy {

    public static final String NAME = "BunkerMain";

    private List<TilePosition> held = new ArrayList<>();

    public BunkerMain() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        held = heldBunkers(TerranBunker.livingBunkerTiles(context), TerranBunker.atEnemyMain(context));
        return !held.isEmpty();
    }

    @Override
    public String getDetectionLabel() {
        return TerranBunker.label(getName(), held);
    }

    /**
     * The living Bunkers that stand at the enemy main.
     */
    static List<TilePosition> heldBunkers(Collection<TilePosition> living, Predicate<TilePosition> atEnemyMain) {
        return TerranBunker.passing(living, atEnemyMain);
    }
}
