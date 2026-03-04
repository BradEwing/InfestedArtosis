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
    private static final int DEFENSE_PUSH_STEP = 32;
    private static final int DEFENSE_PUSH_MAX = 256;

    @Getter private final Position center;
    private final double centerAngle;
    @Getter private final int radius;
    private final int arcDegrees;
    private final int numPoints;

    private int mapPixelWidth;
    private int mapPixelHeight;

    private List<Position> positions = new ArrayList<>();
    private List<Integer> originalIndices = new ArrayList<>();

    public Arc(Position center, Position faceTarget, int radius, int arcDegrees, int numPoints) {
        this.center = center;
        this.radius = radius;
        this.arcDegrees = arcDegrees;
        this.numPoints = Math.max(2, numPoints);

        double dx = faceTarget.getX() - center.getX();
        double dy = faceTarget.getY() - center.getY();
        this.centerAngle = Math.atan2(dy, dx);
    }

    public void compute(Set<WalkPosition> accessibleWalkPositions, Set<Position> staticDefenseCoverage,
                        int mapPixelWidth, int mapPixelHeight) {
        this.mapPixelWidth = mapPixelWidth;
        this.mapPixelHeight = mapPixelHeight;
        positions.clear();
        originalIndices.clear();

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
                candidate = pushOutsideDefenseCoverage(angle, candidate, accessibleWalkPositions, staticDefenseCoverage);
                if (candidate == null) continue;
            }

            positions.add(candidate);
            originalIndices.add(i);
        }

        selectLargestSegment(accessibleWalkPositions);
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
        List<Position> available = prioritizeCenterPositions(units.size());

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

    private List<Position> prioritizeCenterPositions(int unitCount) {
        if (unitCount >= positions.size()) {
            return new ArrayList<>(positions);
        }

        int mid = positions.size() / 2;
        int half = unitCount / 2;
        int start = Math.max(0, mid - half);
        int end = Math.min(positions.size(), start + unitCount);
        if (end - start < unitCount) {
            start = Math.max(0, end - unitCount);
        }

        return new ArrayList<>(positions.subList(start, end));
    }

    private boolean isInStaticDefenseCoverage(Position pos, Set<Position> coverage) {
        if (coverage.isEmpty()) return false;
        int snappedX = (pos.getX() / 8) * 8;
        int snappedY = (pos.getY() / 8) * 8;
        return coverage.contains(new Position(snappedX, snappedY));
    }

    private Position pushOutsideDefenseCoverage(double angle, Position original,
                                                Set<WalkPosition> accessible, Set<Position> coverage) {
        double baseDist = original.getDistance(center);
        for (int delta = DEFENSE_PUSH_STEP; delta <= DEFENSE_PUSH_MAX; delta += DEFENSE_PUSH_STEP) {
            double pushRadius = baseDist + delta;
            int px = center.getX() + (int) (Math.cos(angle) * pushRadius);
            int py = center.getY() + (int) (Math.sin(angle) * pushRadius);
            Position candidate = clampPosition(px, py);
            if (isInStaticDefenseCoverage(candidate, coverage)) continue;
            if (!accessible.isEmpty() && !accessible.contains(new WalkPosition(candidate))) continue;
            return candidate;
        }
        return null;
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

    private void selectLargestSegment(Set<WalkPosition> accessible) {
        if (positions.size() <= 1) return;

        int bestStart = 0;
        int bestLength = 1;
        int currentStart = 0;
        int currentLength = 1;
        int arcCenter = numPoints / 2;

        for (int i = 1; i < originalIndices.size(); i++) {
            if (originalIndices.get(i) == originalIndices.get(i - 1) + 1
                    && isSegmentWalkable(positions.get(i - 1), positions.get(i), accessible)) {
                currentLength++;
            } else {
                if (isBetterSegment(currentStart, currentLength, bestStart, bestLength, arcCenter)) {
                    bestStart = currentStart;
                    bestLength = currentLength;
                }
                currentStart = i;
                currentLength = 1;
            }
        }
        if (isBetterSegment(currentStart, currentLength, bestStart, bestLength, arcCenter)) {
            bestStart = currentStart;
            bestLength = currentLength;
        }

        originalIndices = new ArrayList<>(originalIndices.subList(bestStart, bestStart + bestLength));
        positions = new ArrayList<>(positions.subList(bestStart, bestStart + bestLength));
    }

    private boolean isBetterSegment(int startA, int lengthA, int startB, int lengthB, int arcCenter) {
        if (lengthA != lengthB) return lengthA > lengthB;
        int midA = originalIndices.get(startA + lengthA / 2);
        int midB = originalIndices.get(startB + lengthB / 2);
        return Math.abs(midA - arcCenter) < Math.abs(midB - arcCenter);
    }

    private boolean isSegmentWalkable(Position a, Position b, Set<WalkPosition> accessible) {
        if (accessible.isEmpty()) return true;
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance == 0) return true;
        int numSteps = Math.max(1, (int) Math.ceil(distance / 8.0));
        for (int i = 1; i <= numSteps; i++) {
            double progress = (double) i / numSteps;
            int checkX = a.getX() + (int)(dx * progress);
            int checkY = a.getY() + (int)(dy * progress);
            WalkPosition wp = new WalkPosition(new Position(checkX, checkY));
            if (!accessible.contains(wp)) return false;
        }
        return true;
    }

    private Position clampPosition(int x, int y) {
        int cx = Math.max(0, Math.min(x, mapPixelWidth - 1));
        int cy = Math.max(0, Math.min(y, mapPixelHeight - 1));
        return new Position(cx, cy);
    }
}
