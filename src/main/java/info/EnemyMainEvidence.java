package info;

/**
 * What an enemy building says about which starting location is the enemy main, weakest first. A main assigned
 * from one kind of evidence is replaced only by stronger evidence at another starting location.
 */
public enum EnemyMainEvidence {
    /**
     * A Forge, Photon Cannon or Bunker standing in the BWEM Area of the starting location's natural, the wall
     * that keeps scouts out of a main behind it. Taken only while no enemy depot has been seen on a start.
     */
    NATURAL_AREA,
    /**
     * A building of any type seen while every other starting location but this one has been seen empty, so the
     * enemy can have started nowhere else. Taken only while no enemy depot has been seen on a start.
     */
    LAST_START,
    /**
     * A building of any type standing in the BWEM Area of the starting location. The Area ends at the main's
     * chokes, so a proxy in the open or at a natural never counts, while a building anywhere on the main
     * plateau does, however far it stands from the depot.
     */
    MAIN_AREA,
    /**
     * A resource depot standing on the starting location itself.
     */
    DEPOT;

    public boolean isStrongerThan(EnemyMainEvidence other) {
        return compareTo(other) > 0;
    }
}
