package util;

import bwapi.Position;

/**
 * Keeps flyer flee and retreat points off the map edge, as static functions over plain values.
 *
 * <p>A point clamped to the very edge pins a flock there: the flyers arrive, the order completes, and the next flee
 * point clamps to the same place. A point is instead held {@link #INSET} pixels inside the map, and a flight that the
 * edge would cut short slides along the edge, keeping its length, toward the side with more room. A flight into a
 * corner slides along the edge its direction opposes less.
 */
public final class MapEdge {

    /** Tuning value: pixels a flee or retreat point keeps from every map edge. */
    public static final int INSET = 96;
    /** Tuning value: pixels from an edge within which a flyer counts as parked at it. */
    public static final int BAND = 96;
    /** Tuning value: pixels from the edge a flyer released from the edge band is sent to. */
    public static final int RELEASE_DEPTH = 192;
    /** Tuning value: share of a flight that must survive the edge, else it slides along the edge instead. */
    static final double MIN_KEPT_SHARE = 0.5;
    /** Tuning value: share of a flight's length left along the edge below which the flight turns along it. */
    static final double MIN_ALONG_SHARE = 0.35;

    private MapEdge() {
    }

    /**
     * The point a flight toward a target ends at: the target held {@link #INSET} inside the map when the flight keeps
     * at least {@link #MIN_KEPT_SHARE} of its length, else the flight slid along the edge.
     *
     * @param from where the flight starts
     * @param target where the flight would end, possibly beyond the map
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the end point, inside the inset
     */
    public static Position toward(Position from, Position target, int mapWidth, int mapHeight) {
        double dx = target.getX() - from.getX();
        double dy = target.getY() - from.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) {
            return inset(from, mapWidth, mapHeight);
        }
        Position clamped = inset(target, mapWidth, mapHeight);
        double kept = from.getDistance(clamped);
        if (kept >= MIN_KEPT_SHARE * length) {
            return clamped;
        }
        return slide(from, dx / length, dy / length, length, mapWidth, mapHeight);
    }

    /**
     * The point a flight of a given length in a direction ends at, see {@link #toward}.
     *
     * @param from where the flight starts
     * @param dirX x of the direction, any length but not zero
     * @param dirY y of the direction
     * @param distance pixels to fly
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the end point, inside the inset
     */
    public static Position flee(Position from, double dirX, double dirY, double distance, int mapWidth,
                                int mapHeight) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY);
        if (length == 0) {
            return inset(from, mapWidth, mapHeight);
        }
        double ux = dirX / length;
        double uy = dirY / length;
        Position target = new Position(from.getX() + (int) Math.round(ux * distance),
                from.getY() + (int) Math.round(uy * distance));
        return toward(from, target, mapWidth, mapHeight);
    }

    /**
     * A point held {@link #INSET} inside the map.
     *
     * @param point the point
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the point, moved inside the inset when it was outside
     */
    public static Position inset(Position point, int mapWidth, int mapHeight) {
        return new Position(clamp(point.getX(), mapWidth), clamp(point.getY(), mapHeight));
    }

    /**
     * Whether a point lies inside the map by at least a margin from every edge.
     *
     * @param point the point
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @param margin pixels to keep from every edge
     * @return true when it does
     */
    public static boolean inside(Position point, int mapWidth, int mapHeight, int margin) {
        return point.getX() >= margin && point.getY() >= margin && point.getX() < mapWidth - margin
                && point.getY() < mapHeight - margin;
    }

    /**
     * Whether a point stands within {@link #BAND} of a map edge.
     *
     * @param point the point
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return true when it does
     */
    public static boolean inBand(Position point, int mapWidth, int mapHeight) {
        return point.getX() < BAND || point.getY() < BAND || point.getX() >= mapWidth - BAND
                || point.getY() >= mapHeight - BAND;
    }

    /**
     * Where a flyer parked in the edge band goes: straight in from the edge it stands at to {@link #RELEASE_DEPTH}
     * from it, keeping its position along the edge, and from a corner in from both edges.
     *
     * @param point the flyer's position
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the release point
     */
    public static Position release(Position point, int mapWidth, int mapHeight) {
        return new Position(deepen(point.getX(), mapWidth), deepen(point.getY(), mapHeight));
    }

    private static int deepen(int value, int extent) {
        if (value < BAND) {
            return RELEASE_DEPTH;
        }
        if (value >= extent - BAND) {
            return extent - 1 - RELEASE_DEPTH;
        }
        return value;
    }

    private static Position slide(Position from, double ux, double uy, double length, int mapWidth, int mapHeight) {
        boolean blockedX = blocked(from.getX(), ux, length, mapWidth);
        boolean blockedY = blocked(from.getY(), uy, length, mapHeight);
        double sx = blockedX ? 0 : ux;
        double sy = blockedY ? 0 : uy;
        double along = Math.sqrt(sx * sx + sy * sy);
        if (along < MIN_ALONG_SHARE) {
            if (blockedX && blockedY && Math.abs(ux) <= Math.abs(uy)) {
                sx = Math.signum(mapWidth / 2.0 - from.getX());
                sy = 0;
            } else if (blockedX && blockedY) {
                sx = 0;
                sy = Math.signum(mapHeight / 2.0 - from.getY());
            } else if (blockedX) {
                sx = 0;
                sy = uy != 0 ? Math.signum(uy) : Math.signum(mapHeight / 2.0 - from.getY());
            } else {
                sx = ux != 0 ? Math.signum(ux) : Math.signum(mapWidth / 2.0 - from.getX());
                sy = 0;
            }
            along = Math.sqrt(sx * sx + sy * sy);
        }
        if (along == 0) {
            return inset(from, mapWidth, mapHeight);
        }
        double scale = length / along;
        Position target = new Position(from.getX() + (int) Math.round(sx * scale),
                from.getY() + (int) Math.round(sy * scale));
        return inset(target, mapWidth, mapHeight);
    }

    private static boolean blocked(int start, double unit, double length, int extent) {
        double end = start + unit * length;
        return unit < 0 && end < INSET || unit > 0 && end > extent - 1 - INSET;
    }

    private static int clamp(int value, int extent) {
        return Math.max(INSET, Math.min(value, extent - 1 - INSET));
    }
}
