package info.tracking;

import bwapi.Bullet;
import bwapi.BulletType;
import bwapi.Position;
import lombok.Data;
import util.Time;

@Data
public class ObservedBullet {
    private Time firstObservedFrame;
    private Time lastObservedFrame;
    private Time destroyedFrame;
    private Position lastKnownLocation;
    private final Bullet bullet;
    private BulletType bulletType;

    public ObservedBullet(Bullet bullet, Time currentFrame) {
        this.bullet = bullet;
        this.bulletType = bullet.getType();
        this.firstObservedFrame = currentFrame;
        this.lastObservedFrame = currentFrame;
        this.lastKnownLocation = bullet.getPosition();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObservedBullet that = (ObservedBullet) o;
        return bullet.equals(that.bullet);
    }

    @Override
    public int hashCode() {
        return bullet.hashCode();
    }
}
