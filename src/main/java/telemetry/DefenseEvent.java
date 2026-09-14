package telemetry;

/**
 * What worker defence did at a base on the frame a defence squad row is written.
 */
public enum DefenseEvent {
    /**
     * Gatherers were pulled into the defence squad.
     */
    PULL,

    /**
     * The full commitment of assigned defenders and candidates lost the simulated fight, so no gatherer was
     * pulled and every assigned defender was released to mine.
     */
    ABANDON,

    /**
     * The base has no threats left, or the defence was called off by a cannon rush reaction, and the assigned
     * defenders were released to mine.
     */
    RELEASE
}
