package info.map;

import bwapi.TilePosition;
import info.exception.NoWalkablePathException;
import info.map.search.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.IntStream;

/**
 * Bundles up information about the game map.
 */
public class GameMap {

    private int x;
    private int y;
    private ArrayList<TileInfo> heatMap = new ArrayList<>();

    private TileInfo[][] mapTiles;

    public GameMap(int x, int y) {
        mapTiles = new TileInfo[x][y];
        this.x = x;
        this.y = y;
    }

    public void addTile(TileInfo tile, int x, int y) {
        mapTiles[x][y] = tile;
        heatMap.add(tile);
    }

    public ArrayList<TileInfo> getHeapMap() {
        return heatMap;
    }

    /**
     * A* search to find a walkable path between start and end tiles.
     *
     * TODO: Consider neutral structures in TileInfo
     *
     * @param start origin TilePosition
     * @param end destination TilePosition
     * @return
     * @throws NoWalkablePathException if no walkable path exists
     */
    public ArrayDeque<TileInfo> aStarSearch(TileInfo start, TileInfo end) throws NoWalkablePathException {
        Map<TileInfo, TileInfo> cameFrom = new HashMap<>();
        Map<TileInfo, Integer> gScore = new HashMap<>();
        Map<TileInfo, Integer> fScore = new HashMap<>();

        gScore.put(start, 0);
        fScore.put(start, this.calculateH(start, end));

        PriorityQueue<TileInfo> openSet = new PriorityQueue<>(new TileFScoreComparator(fScore));
        openSet.add(start);

        while (!openSet.isEmpty()) {
            TileInfo current = openSet.poll();
            if (current == end) {
                return reconstructPath(cameFrom, current);
            }

            List<TileInfo> neighbors = this.getNeighbors(current);

            // Determine tentative gScore
            // Compare tentative gScore against gScore of neighbor

            // If tentative g Score is less, set cameFrom, gScore and fScore for neighbor
            // If neighbor not in openSet, add to openSet

        }

        throw new NoWalkablePathException("no walkable path exists");
    }

    private int calculateH(TileInfo current,  TileInfo destination) {
        return (int) destination.getTile().getDistance(current.getTile());
    }

    private ArrayDeque<TileInfo> reconstructPath(Map<TileInfo, TileInfo> cameFrom, TileInfo current) {
        ArrayDeque<TileInfo> path = new ArrayDeque<>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.addFirst(current);
        }

        return path;
    }

    /**
     * Returns neighbor tiles of current that can be considered for ground based path-finding.
     *
     * @param current
     * @return
     */
    private List<TileInfo> getNeighbors(TileInfo current) {
        List<TileInfo> neighbors = new ArrayList<>();

        final int currentX = current.getX();
        final int currentY = current.getY();

        IntStream.range(-1, 1).forEachOrdered(i -> IntStream.range(-1, 1).forEachOrdered(j -> {
            final int neighborX = currentX+i;
            final int neighborY = currentY+j;
            if (!isValidTile(neighborX,neighborY)) {
                return;
            }
            if (neighborX == currentX && neighborY == currentY) {
                return;
            }

            final TileInfo candidate = mapTiles[neighborX][neighborY];
            if (!candidate.isWalkable()) {
                return;
            }

            neighbors.add(candidate);
        }));


        return neighbors;
    }

    private boolean isValidTile(int x, int y) {
        return x > 0 && x <= this.x && y > 0 && y <= this.y;
    }

    private void calculateG(Node node) {
        this.G = node.getG() + (int)  node.getTilePosition().getDistance(tilePosition);
    }
}
