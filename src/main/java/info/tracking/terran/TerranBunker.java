package info.tracking.terran;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.tracking.StrategyDetectionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * What the Bunker detectors share. They read the Bunkers the tracker still holds as living, a Bunker last seen alive
 * where it was seen counting until it is seen destroyed or its tile is seen empty, and place each one by the tile
 * under its centre. A Bunker belongs to the enemy main when it stands in the main's BWEM Area or within
 * {@link TerranWallMain#CHOKE_TILE_RADIUS} of a chokepoint of that Area, the ramp among them, and otherwise to the
 * enemy natural when it stands in the natural's area as {@link TerranWallNatural} reads it, so one Bunker is never
 * both.
 */
public final class TerranBunker {

    /**
     * Manhattan tiles around the natural depot within which a Bunker belongs to the natural.
     */
    static final int NATURAL_PROXIMITY_TILE_RADIUS = 8;

    /**
     * Manhattan tiles around the natural depot within which a Bunker on the natural's BWEM ground belongs to it.
     */
    static final int NATURAL_AREA_TILE_RADIUS = 20;

    private TerranBunker() {
    }

    /**
     * The tiles of the living Bunkers the tracker knows a position for, ordered by x then y.
     */
    static List<TilePosition> livingBunkerTiles(StrategyDetectionContext context) {
        List<TilePosition> tiles = new ArrayList<>();
        for (Position position : context.getTracker().getLastKnownPositionsOfLivingUnits(UnitType.Terran_Bunker)) {
            tiles.add(position.toTilePosition());
        }
        tiles.sort(Comparator.comparingInt(TilePosition::getX).thenComparingInt(TilePosition::getY));
        return tiles;
    }

    /**
     * The Bunker tiles that pass the test, in order.
     */
    static List<TilePosition> passing(Collection<TilePosition> bunkers, Predicate<TilePosition> test) {
        return bunkers.stream().filter(test).collect(Collectors.toList());
    }

    /**
     * Whether a Bunker at the tile belongs to the enemy main. False while the enemy main is unknown.
     */
    static Predicate<TilePosition> atEnemyMain(StrategyDetectionContext context) {
        Predicate<TilePosition> inMainArea = context.enemyMainArea();
        if (inMainArea == null) {
            return tile -> false;
        }
        return inMainArea.or(TerranWallMain.atMainChoke(context));
    }

    /**
     * The detection label: the name, then the count of held Bunkers and the tile of the first, as
     * BunkerNatural:2@120x45. The name alone when none is held.
     */
    static String label(String name, List<TilePosition> held) {
        if (held.isEmpty()) {
            return name;
        }
        TilePosition first = held.get(0);
        return name + ":" + held.size() + "@" + first.getX() + "x" + first.getY();
    }
}
