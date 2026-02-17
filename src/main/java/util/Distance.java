package util;

import bwapi.TilePosition;

public final class Distance {

    private Distance() {}

    public static int manhattanTile(TilePosition a, TilePosition b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
}
