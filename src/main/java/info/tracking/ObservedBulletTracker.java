package info.tracking;

import bwapi.Bullet;
import bwapi.BulletType;
import bwapi.Game;
import bwapi.Position;
import util.Time;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ObservedBulletTracker {
    private final HashMap<Bullet, ObservedBullet> observedBullets = new HashMap<>();

    private static final HashMap<BulletType, Integer> BULLET_DURATIONS = new HashMap<>();

    static {
        BULLET_DURATIONS.put(BulletType.Psionic_Storm, 72);
    }

    public ObservedBulletTracker() {

    }

    public void onFrame(Game game, int currentFrame) {
        if (game.getBullets() == null) {
            return;
        }

        Time t = new Time(currentFrame);

        HashSet<Bullet> activeBullets = new HashSet<>();
        for (Bullet bullet : game.getBullets()) {
            if (bullet == null) {
                continue;
            }
            BulletType type = bullet.getType();
            if (bullet.exists()) {
                activeBullets.add(bullet);
                if (!observedBullets.containsKey(bullet)) {
                    observedBullets.put(bullet, new ObservedBullet(bullet, t));
                } else {
                    ObservedBullet ob = observedBullets.get(bullet);
                    if (ob.getBulletType() != type) {
                        observedBullets.put(bullet, new ObservedBullet(bullet, t));
                        continue;
                    }
                    ob.setLastObservedFrame(t);
                    ob.setLastKnownLocation(bullet.getPosition());
                    ob.setDestroyedFrame(null);
                }
            }
        }

        observedBullets.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getDestroyedFrame() == null)
                .filter(entry -> !activeBullets.contains(entry.getKey()))
                .filter(entry -> hasExceededDuration(entry.getValue(), currentFrame))
                .forEach(entry -> entry.getValue().setDestroyedFrame(t));
    }

    private boolean hasExceededDuration(ObservedBullet bullet, int currentFrame) {
        Integer duration = BULLET_DURATIONS.get(bullet.getBulletType());
        if (duration == null) {
            return true;
        }
        int elapsedFrames = currentFrame - bullet.getFirstObservedFrame().getFrames();
        return elapsedFrames >= duration;
    }

    public int size() {
        return observedBullets.size();
    }

    public int getCountOfActiveBullets(BulletType bulletType) {
        return (int) observedBullets.values()
                .stream()
                .filter(ob -> ob.getBulletType() == bulletType)
                .filter(ob -> ob.getDestroyedFrame() == null)
                .count();
    }

    public Set<ObservedBullet> getActiveBullets(BulletType bulletType) {
        return observedBullets.values()
                .stream()
                .filter(ob -> ob.getBulletType() == bulletType)
                .filter(ob -> ob.getDestroyedFrame() == null)
                .collect(Collectors.toSet());
    }

    public Set<Position> getActiveBulletPositions(BulletType bulletType) {
        return observedBullets.values()
                .stream()
                .filter(ob -> ob.getBulletType() == bulletType)
                .filter(ob -> ob.getDestroyedFrame() == null)
                .map(ObservedBullet::getLastKnownLocation)
                .filter(pos -> pos != null)
                .collect(Collectors.toSet());
    }

    public Set<ObservedBullet> getAllActiveBullets() {
        return observedBullets.values()
                .stream()
                .filter(ob -> ob.getDestroyedFrame() == null)
                .collect(Collectors.toSet());
    }

    public int getBulletTypeCountBeforeTime(BulletType type, Time t) {
        return (int) observedBullets.values()
                .stream()
                .filter(ob -> ob.getBulletType() == type)
                .filter(ob -> ob.getFirstObservedFrame().lessThanOrEqual(t))
                .count();
    }
}
