package info.map;

import bwapi.TilePosition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Determines the ground distance of a given path of map tiles.
 */
public class GroundPath {

    private List<MapTile> path = new ArrayList<>();

    private int groundDistance = 0;

    public GroundPath(ArrayDeque<MapTile> waypoints) {
        for (MapTile t: waypoints) {
            path.add(t);
        }

        for (int i = 0; i < path.size() - 1; i++) {
            final TilePosition tpX = path.get(i).getTile();
            final TilePosition tpY = path.get(i + 1).getTile();
            final int d = (int) tpX.getDistance(tpY);
            groundDistance += d;
        }
    }

    public int getGroundDistance() {
        return this.groundDistance;
    }

    public List<MapTile> getPath() {
        return this.path;
    }
}
