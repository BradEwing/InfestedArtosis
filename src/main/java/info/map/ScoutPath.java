package info.map;

import bwapi.TilePosition;

import java.util.List;

public class ScoutPath {

    private List<TilePosition> points;
    private int current = 0;

    public ScoutPath(List<TilePosition> points) {
        this.points = points;
    }

    public TilePosition next() {
        current += 1;
        if (current >= points.size()) {
            current = 0;
        }

        return points.get(current);
    }
}
