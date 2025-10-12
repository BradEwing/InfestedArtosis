package unit.squad;

import util.Time;

public class ZerglingSquad extends Squad {
    private int fightLockedUntilFrame = 0;
    private int retreatLockedUntilFrame = 0;
    private Time fightHysteresis = new Time(0, 3); // ~72 frames
    private Time retreatHysteresis = new Time(0, 5); // ~120 frames

    public boolean isFightLocked(int currentFrame) {
        return currentFrame < fightLockedUntilFrame;
    }

    public boolean isRetreatLocked(int currentFrame) {
        return currentFrame < retreatLockedUntilFrame;
    }

    public void startFightLock(int currentFrame) {
        fightLockedUntilFrame = currentFrame + fightHysteresis.getFrames();
    }

    public void startRetreatLock(int currentFrame) {
        retreatLockedUntilFrame = currentFrame + retreatHysteresis.getFrames();
    }

    public void setFightHysteresis(Time duration) {
        this.fightHysteresis = duration;
    }

    public void setRetreatHysteresis(Time duration) {
        this.retreatHysteresis = duration;
    }
}


