package config;

/**
 * Feature flags to toggle experimental features.
 *
 */
public final class Config {

    public static boolean learnDefensiveSunk = false;
    public boolean enabledAutoObserver = false;

    public Config() {
        this.enabledAutoObserver = Boolean.parseBoolean(System.getenv("IA_ENABLE_AUTO_OBSERVER"));
    }
}
