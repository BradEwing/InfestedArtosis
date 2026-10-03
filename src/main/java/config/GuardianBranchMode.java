package config;

/**
 * How the Guardian build is offered at the handover to a terminal ZvT build, set by
 * IA_GUARDIAN_BRANCH.
 */
public enum GuardianBranchMode {

    /** Both terminal builds are candidates and the learning module chooses; the default. */
    LEARNED,

    /** Only the Guardian build is offered. */
    ON,

    /** Only the Defiler and Ultralisk build is offered. */
    OFF;

    /**
     * Reads the raw setting.
     *
     * @param value the raw setting, or null when it is not set
     * @return {@link #ON} for the word true, {@link #OFF} for the word false, in any case,
     *     otherwise {@link #LEARNED}
     */
    public static GuardianBranchMode parse(String value) {
        if (value == null) {
            return LEARNED;
        }
        String word = value.trim();
        if ("true".equalsIgnoreCase(word)) {
            return ON;
        }
        return "false".equalsIgnoreCase(word) ? OFF : LEARNED;
    }
}
