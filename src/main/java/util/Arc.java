package util;

import bwapi.Position;
import bwapi.WalkPosition;
import lombok.Getter;
import unit.managed.ManagedUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Arc {
    private static final int MIN_RADIUS = 32;
    private static final int WALKABLE_SEARCH_STEP = 32;
    private static final int WALKABLE_SEARCH_MAX = 128;
    private static final int COVERAGE_SHIFT_STEP = 32;
    private static final int COVERAGE_SHIFT_MAX = 192;

    @Getter private final Position center;
    private final double centerAngle;
    @Getter private final int radius;
    private final int arcDegrees;
    private final int numPoints;

    private List<Position> positions = new ArrayList<>();

    public Arc(Position center, Position faceTarget, int radius, int arcDegrees, int numPoints) {
        this.center = center;
        this.radius = radius;
        this.arcDegrees = arcDegrees;
        this.numPoints = Math.max(2, numPoints);

        double dx = faceTarget.getX() - center.getX();
        double dy = faceTarget.getY() - center.getY();
        this.centerAngle = Math.atan2(dy, dx);
    }

    public void compute(Set<WalkPosition> accessibleWalkPositions, Set<Position> staticDefenseCoverage) {
        positions.clear();

        double halfArc = Math.toRadians(arcDegrees / 2.0);
        double startAngle = centerAngle - halfArc;
        double angleStep = Math.toRadians((double) arcDegrees / (numPoints - 1));

        for (int i = 0; i < numPoints; i++) {
            double angle = startAngle + (i * angleStep);
            int px = center.getX() + (int) (Math.cos(angle) * radius);
            int py = center.getY() + (int) (Math.sin(angle) * radius);
            Position candidate = clampPosition(px, py);

            if (!accessibleWalkPositions.isEmpty()) {
                WalkPosition wp = new WalkPosition(candidate);
                if (!accessibleWalkPositions.contains(wp)) {
                    candidate = findWalkableAlternative(angle, accessibleWalkPositions);
                    if (candidate == null) continue;
                }
            }

            if (isInStaticDefenseCoverage(candidate, staticDefenseCoverage)) {
                candidate = shiftOutsideCoverage(candidate, angle, accessibleWalkPositions, staticDefenseCoverage);
                if (candidate == null) continue;
            }

            positions.add(candidate);
        }
    }

    public List<Position> getPositions() {
        return Collections.unmodifiableList(positions);
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public int size() {
        return positions.size();
    }

    public Map<ManagedUnit, Position> assignUnits(List<ManagedUnit> units) {
        Map<ManagedUnit, Position> assignments = new LinkedHashMap<>();
        List<Position> available = new ArrayList<>(positions);

        for (ManagedUnit unit : units) {
            if (available.isEmpty()) break;

            Position closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Position pos : available) {
                double dist = unit.getPosition().getDistance(pos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = pos;
                }
            }

            if (closest != null) {
                assignments.put(unit, closest);
                available.remove(closest);
            }
        }

        return assignments;
    }

    private boolean isInStaticDefenseCoverage(Position pos, Set<Position> coverage) {
        if (coverage.isEmpty()) return false;
        int snappedX = (pos.getX() / 8) * 8;
        int snappedY = (pos.getY() / 8) * 8;
        return coverage.contains(new Position(snappedX, snappedY));
    }

    private Position findWalkableAlternative(double angle, Set<WalkPosition> accessible) {
        for (int deltaR = -WALKABLE_SEARCH_STEP; deltaR >= -WALKABLE_SEARCH_MAX; deltaR -= WALKABLE_SEARCH_STEP) {
            int adjustedRadius = radius + deltaR;
            if (adjustedRadius < MIN_RADIUS) continue;

            int px = center.getX() + (int) (Math.cos(angle) * adjustedRadius);
            int py = center.getY() + (int) (Math.sin(angle) * adjustedRadius);
            Position candidate = clampPosition(px, py);

            if (accessible.contains(new WalkPosition(candidate))) {
                return candidate;
            }
        }

        for (int deltaDeg = 5; deltaDeg <= 20; deltaDeg += 5) {
            for (int sign = -1; sign <= 1; sign += 2) {
                double adjustedAngle = angle + Math.toRadians(deltaDeg * sign);
                int px = center.getX() + (int) (Math.cos(adjustedAngle) * radius);
                int py = center.getY() + (int) (Math.sin(adjustedAngle) * radius);
                Position candidate = clampPosition(px, py);

                if (accessible.contains(new WalkPosition(candidate))) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private Position shiftOutsideCoverage(Position original, double angle, Set<WalkPosition> accessible, Set<Position> coverage) {
        double reverseAngle = angle + Math.PI;
        for (int delta = COVERAGE_SHIFT_STEP; delta <= COVERAGE_SHIFT_MAX; delta += COVERAGE_SHIFT_STEP) {
            int px = original.getX() + (int) (Math.cos(reverseAngle) * delta);
            int py = original.getY() + (int) (Math.sin(reverseAngle) * delta);
            Position candidate = clampPosition(px, py);

            if (!accessible.isEmpty() && !accessible.contains(new WalkPosition(candidate))) {
                continue;
            }
            if (!isInStaticDefenseCoverage(candidate, coverage)) {
                return candidate;
            }
        }

        return null;
    }

    private Position clampPosition(int x, int y) {
        return new Position(Math.max(0, x), Math.max(0, y));
    }
}
