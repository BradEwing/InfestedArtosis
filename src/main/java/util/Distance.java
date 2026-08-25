package util;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwem.Base;
import unit.managed.ManagedUnit;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class Distance {

    private Distance() {}

    public static int manhattanTileDistance(TilePosition a, TilePosition b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    public static Set<TilePosition> tilesWithinManhattanDistance(TilePosition center, int radius) {
        Set<TilePosition> tiles = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            int remaining = radius - Math.abs(dx);
            for (int dy = -remaining; dy <= remaining; dy++) {
                tiles.add(new TilePosition(center.getX() + dx, center.getY() + dy));
            }
        }
        return tiles;
    }

    public static double euclidean(Position a, Position b) {
        return a.getDistance(b);
    }

    public static double euclidean(int x1, int y1, int x2, int y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static boolean isWithinRange(int x1, int y1, int x2, int y2, int range) {
        long dx = x1 - x2;
        long dy = y1 - y2;
        return dx * dx + dy * dy <= (long) range * range;
    }

    public static Comparator<Unit> closestTo(Unit target) {
        return (a, b) -> Integer.compare(target.getDistance(a), target.getDistance(b));
    }

    public static Comparator<Base> closestBaseTo(Unit target) {
        return (a, b) -> Double.compare(
                target.getDistance(a.getLocation().toPosition()),
                target.getDistance(b.getLocation().toPosition())
        );
    }

    public static Comparator<TilePosition> closestTo(TilePosition target) {
        return (a, b) -> Double.compare(target.getDistance(a), target.getDistance(b));
    }

    public static Comparator<ManagedUnit> closestManagedUnitTo(Position target) {
        return (a, b) -> Double.compare(
                target.getDistance(a.getUnit().getPosition()),
                target.getDistance(b.getUnit().getPosition())
        );
    }
}
