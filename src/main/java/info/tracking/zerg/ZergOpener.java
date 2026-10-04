package info.tracking.zerg;

/**
 * Zerg openers {@link ZergOpenerReading} tells apart, by the name each is recorded under and the timing sign it is
 * read on.
 */
public enum ZergOpener {
    NINE_POOL("9Pool", "EARLY_POOL"),
    TWELVE_POOL("12Pool", "LATE_POOL"),
    TWELVE_HATCH("12Hatch", "HATCH_FIRST");

    private final String strategyName;
    private final String sign;

    ZergOpener(String strategyName, String sign) {
        this.strategyName = strategyName;
        this.sign = sign;
    }

    public String getStrategyName() {
        return strategyName;
    }

    /**
     * The timing sign the opener is read on, as telemetry records it.
     */
    public String getSign() {
        return sign;
    }
}
