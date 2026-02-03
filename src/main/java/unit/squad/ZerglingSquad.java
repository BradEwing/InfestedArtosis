package unit.squad;

import util.Time;

public class ZerglingSquad extends Squad {

    public void setFightHysteresis(Time duration) {
        this.fightHysteresis = duration;
    }

    public void setRetreatHysteresis(Time duration) {
        this.retreatHysteresis = duration;
    }
}


