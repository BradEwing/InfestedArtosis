package info.tracking;

import bwapi.BulletType;
import bwapi.Position;

import java.util.Set;

public class PsiStormTracker {
    private final ObservedBulletTracker bulletTracker;

    private static final int STORM_DURATION_FRAMES = 72;
    public static final int STORM_RADIUS = 96;

    public PsiStormTracker(ObservedBulletTracker bulletTracker) {
        this.bulletTracker = bulletTracker;
    }

    public Set<Position> getActiveStormPositions() {
        return bulletTracker.getActiveBulletPositions(BulletType.Psionic_Storm);
    }

    public boolean isPositionInStorm(Position pos, int buffer) {
        int effectiveRadius = STORM_RADIUS + buffer;
        for (ObservedBullet storm : bulletTracker.getActiveBullets(BulletType.Psionic_Storm)) {
            Position stormPos = storm.getLastKnownLocation();
            if (stormPos != null) {
                double distance = pos.getDistance(stormPos);
                if (distance <= effectiveRadius) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getActiveStormCount() {
        return bulletTracker.getCountOfActiveBullets(BulletType.Psionic_Storm);
    }

    public Set<ObservedBullet> getActiveStorms() {
        return bulletTracker.getActiveBullets(BulletType.Psionic_Storm);
    }

    public int getRemainingFrames(ObservedBullet storm, int currentFrame) {
        int expiresFrame = storm.getFirstObservedFrame().getFrames() + STORM_DURATION_FRAMES;
        return Math.max(0, expiresFrame - currentFrame);
    }
}
