package unit.managed;

import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import info.map.GameMap;
import util.Filter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Overlord behavior: avoids air threats while scouting/retreating and cannot fight.
 */
public class Overlord extends ManagedUnit {
    public Overlord(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
        this.setCanFight(false);
    }

    /**
     * Uses air-capable threats only.
     */
    @Override
    protected List<Unit> getEnemiesInRadius(int currentX, int currentY) {
        return game.getUnitsInRadius(currentX, currentY, 192)
                .stream()
                .filter(u -> u.getPlayer() != game.self())
                .filter(u -> isAirThreat(u.getType()))
                .collect(Collectors.toList());
    }

    private boolean isAirThreat(UnitType type) { return Filter.isAirThreat(type); }

    /**
     * Computes an air-safe retreat without ground walkability checks.
     */
    @Override
    public Position getRetreatPosition() {
        int currentX = unit.getX();
        int currentY = unit.getY();
        List<Unit> enemies = getEnemiesInRadius(currentX, currentY);
        if (enemies.isEmpty()) {
            return new Position(currentX, currentY);
        }
        int sumDx = 0;
        int sumDy = 0;
        for (Unit enemy : enemies) {
            sumDx += enemy.getX() - currentX;
            sumDy += enemy.getY() - currentY;
        }
        int retreatDx = -sumDx;
        int retreatDy = -sumDy;
        double length = Math.max(1.0, Math.sqrt(retreatDx * retreatDx + retreatDy * retreatDy));
        double scale = 128.0 / length;
        int newX = currentX + (int) (retreatDx * scale);
        int newY = currentY + (int) (retreatDy * scale);
        newX = Math.max(0, Math.min(newX, game.mapWidth() * 32 - 1));
        newY = Math.max(0, Math.min(newY, game.mapHeight() * 32 - 1));
        return new Position(newX, newY);
    }

    /**
     * Threat-aware scouting: move away from enemies; if possible, also progress toward scout target.
     */
    @Override
    protected void scout() {
        if (movementTargetPosition == null) {
            return;
        }

        Position current = unit.getPosition();
        List<Unit> threats = getEnemiesInRadius(current.getX(), current.getY());
        if (!threats.isEmpty()) {
            Position away = getRetreatPosition();
            Position target = movementTargetPosition.toPosition();

            double ax = away.getX() - current.getX();
            double ay = away.getY() - current.getY();
            double at = Math.max(1.0, Math.sqrt(ax * ax + ay * ay));
            ax /= at; ay /= at;

            double tx = target.getX() - current.getX();
            double ty = target.getY() - current.getY();
            double tt = Math.max(1.0, Math.sqrt(tx * tx + ty * ty));
            tx /= tt; ty /= tt;

            double dot = ax * tx + ay * ty;
            double bx = ax;
            double by = ay;
            if (dot > 0) {
                bx = ax + 0.5 * tx;
                by = ay + 0.5 * ty;
                double bt = Math.max(1.0, Math.sqrt(bx * bx + by * by));
                bx /= bt; by /= bt;
            }

            int destX = current.getX() + (int) (bx * 128);
            int destY = current.getY() + (int) (by * 128);
            destX = Math.max(0, Math.min(destX, game.mapWidth() * 32 - 1));
            destY = Math.max(0, Math.min(destY, game.mapHeight() * 32 - 1));

            setUnready();
            unit.move(new Position(destX, destY));
            return;
        }

        setUnready();
        unit.move(movementTargetPosition.toPosition());
    }
}
