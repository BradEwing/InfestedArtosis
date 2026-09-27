package info.tracking.zerg;

/**
 * Zerg openers {@link ZergOpenerReading} tells apart, by the name each is recorded under.
 */
public enum ZergOpener {
    NINE_POOL("9Pool"),
    TWELVE_POOL("12Pool"),
    TWELVE_HATCH("12Hatch");

    private final String strategyName;

    ZergOpener(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getStrategyName() {
        return strategyName;
    }
}
