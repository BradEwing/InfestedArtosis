package util;

import bwapi.Game;
import bwapi.Position;

public final class Vec2 {

    public static final Vec2 ZERO = new Vec2(0, 0);

    public final double x;
    public final double y;

    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Vec2 between(Position from, Position to) {
        return new Vec2(to.getX() - from.getX(), to.getY() - from.getY());
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public Vec2 normalize() {
        double len = length();
        if (len == 0) {
            return ZERO;
        }
        return new Vec2(x / len, y / len);
    }

    public Vec2 scale(double factor) {
        return new Vec2(x * factor, y * factor);
    }

    public Vec2 normalizeToLength(double targetLength) {
        double len = length();
        if (len == 0) {
            return ZERO;
        }
        double scale = targetLength / len;
        return new Vec2(x * scale, y * scale);
    }

    public Vec2 perpendicular() {
        return new Vec2(-y, x);
    }

    public Position toPosition(Position origin) {
        return new Position(origin.getX() + (int) x, origin.getY() + (int) y);
    }

    public Position clampToMap(Game game, Position origin) {
        int maxX = game.mapWidth() * 32 - 1;
        int maxY = game.mapHeight() * 32 - 1;
        int px = Math.max(0, Math.min(origin.getX() + (int) x, maxX));
        int py = Math.max(0, Math.min(origin.getY() + (int) y, maxY));
        return new Position(px, py);
    }
}
