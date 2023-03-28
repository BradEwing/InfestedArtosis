package info.map.search;

import bwapi.Point;
import bwapi.TilePosition;
import lombok.Data;

/**
 * Used for path finding
 */
@Data
public class Node {

    private TilePosition tilePosition;

    private Node parent;

    private int G;
    private int H;

    private boolean isWalkable;

    public Node(TilePosition tp, boolean isWalkable) {
        this.tilePosition = tp;
    }

    public boolean isWalkable() {
        return tilePosition.
    }

    public void calculateH(Node destination) {
        this.H = (int) destination.getTilePosition().getDistance(tilePosition);
    }

    public void calculateG(Node node) {
        this.G = node.getG() + (int)  node.getTilePosition().getDistance(tilePosition);
    }

    public int calculateF() {
        return this.G + this.H;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TilePosition tp = ((Node) o).getTilePosition();
        return this.tilePosition == tp;
    }
}
