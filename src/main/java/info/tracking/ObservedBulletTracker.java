package info.tracking;

import bwapi.Bullet;
import bwapi.BulletType;
import bwapi.Game;
import bwapi.Position;
import util.Time;

import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

public class ObservedBulletTracker {
    private final HashMap<Integer, ObservedBullet> observedBullets = new HashMap<>();

    public ObservedBulletTracker() {

    }

    public void onFrame(Game game, int currentFrame) {
        if (game.getBullets() == null) {
            return;
        }

        Time t = new Time(currentFrame);

        for (Bullet bullet : game.getBullets()) {
            if (bullet != null && bullet.exists()) {
                int bulletId = bullet.getID();
                if (!observedBullets.containsKey(bulletId)) {
                    observedBullets.put(bulletId, new ObservedBullet(bullet, t));
                } else {
                    ObservedBullet ob = observedBullets.get(bulletId);
                    ob.setLastObservedFrame(t);
                    ob.setLastKnownLocation(bullet.getPosition());
                }
            }
        }

        Set<Integer> activeBulletIds = game.getBullets().stream()
                .filter(b -> b != null && b.exists())
                .map(Bullet::getID)
                .collect(Collectors.toSet());

        observedBullets.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getDestroyedFrame() == null)
                .filter(entry -> !activeBulletIds.contains(entry.getKey()))
                .forEach(entry -> entry.getValue().setDestroyedFrame(t));
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
