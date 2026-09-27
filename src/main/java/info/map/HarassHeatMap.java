package info.map;

import bwapi.Position;
import bwapi.TilePosition;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Per-tile heat that tells a harassing air squad where it has not been lately, modelled on the scout heat map in
 * {@link GameMap}.
 *
 * <p>On every {@link #accumulate} call each tile within {@link #RADIUS_TILES} of a known enemy base gains
 * {@link #BASE_WEIGHT}, and a tile under a mineral field or a Vespene geyser gains {@link #RESOURCE_WEIGHT} instead,
 * so the mineral line and the geysers of a base are its hottest ground. {@link #visit} zeroes the tiles a harassing
 * unit has passed over, so the squad moves on to ground it has not covered. Tiles away from every enemy base never
 * gain heat. The weights and radius are tuning values, not Brood War facts.
 */
public class HarassHeatMap {

    public static final int RADIUS_TILES = 12;
    public static final double BASE_WEIGHT = 1;
    public static final double RESOURCE_WEIGHT = 3;

    private final int width;
    private final int height;
    private final double[][] heat;

    public HarassHeatMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.heat = new double[width][height];
    }

    /**
     * Adds one step of heat around every known enemy base.
     *
     * <p>A tile near two bases gains once, not twice.
     *
     * @param baseCenters centers of the known enemy bases
     * @param isResourceTile true for a tile under a mineral field or a geyser
     */
    public void accumulate(Collection<Position> baseCenters, Predicate<TilePosition> isResourceTile) {
        boolean[][] touched = new boolean[width][height];
        for (Position center : baseCenters) {
            TilePosition origin = center.toTilePosition();
            for (int x = origin.getX() - RADIUS_TILES; x <= origin.getX() + RADIUS_TILES; x++) {
                for (int y = origin.getY() - RADIUS_TILES; y <= origin.getY() + RADIUS_TILES; y++) {
                    if (!isValid(x, y) || touched[x][y] || !withinRadius(origin, x, y, RADIUS_TILES)) {
                        continue;
                    }
                    touched[x][y] = true;
                    heat[x][y] += isResourceTile.test(new TilePosition(x, y)) ? RESOURCE_WEIGHT : BASE_WEIGHT;
                }
            }
        }
    }

    /**
     * Zeroes every tile within a radius of a point a harassing unit stands on.
     *
     * @param position the unit's position
     * @param radiusTiles radius in tiles
     */
    public void visit(Position position, int radiusTiles) {
        TilePosition origin = position.toTilePosition();
        for (int x = origin.getX() - radiusTiles; x <= origin.getX() + radiusTiles; x++) {
            for (int y = origin.getY() - radiusTiles; y <= origin.getY() + radiusTiles; y++) {
                if (isValid(x, y) && withinRadius(origin, x, y, radiusTiles)) {
                    heat[x][y] = 0;
                }
            }
        }
    }

    /**
     * @param tile a tile
     * @return the tile's heat, or 0 off the map
     */
    public double heatAt(TilePosition tile) {
        return isValid(tile.getX(), tile.getY()) ? heat[tile.getX()][tile.getY()] : 0;
    }

    /**
     * The hottest tile within {@link #RADIUS_TILES} of a base whose center the filter admits. Ties keep the tile
     * nearest the base.
     *
     * @param baseCenter center of the base
     * @param allowed which tile centers may be chosen
     * @return the center of the hottest admitted tile with heat above 0, or null when there is none
     */
    public Position hottestNear(Position baseCenter, Predicate<Position> allowed) {
        TilePosition origin = baseCenter.toTilePosition();
        Position best = null;
        double bestHeat = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int x = origin.getX() - RADIUS_TILES; x <= origin.getX() + RADIUS_TILES; x++) {
            for (int y = origin.getY() - RADIUS_TILES; y <= origin.getY() + RADIUS_TILES; y++) {
                if (!isValid(x, y) || !withinRadius(origin, x, y, RADIUS_TILES) || heat[x][y] <= 0) {
                    continue;
                }
                Position center = new Position(x * 32 + 16, y * 32 + 16);
                if (!allowed.test(center)) {
                    continue;
                }
                double distance = center.getDistance(baseCenter);
                if (heat[x][y] > bestHeat || heat[x][y] == bestHeat && distance < bestDistance) {
                    best = center;
                    bestHeat = heat[x][y];
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private boolean isValid(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private static boolean withinRadius(TilePosition origin, int x, int y, int radius) {
        int dx = x - origin.getX();
        int dy = y - origin.getY();
        return dx * dx + dy * dy <= radius * radius;
    }
}
