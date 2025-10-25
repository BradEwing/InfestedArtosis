package config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration settings loaded from environment variables via .env file.
 * Includes feature flags and override settings for testing and development.
 */
public final class Config {

    public static boolean learnDefensiveSunk = false;
    public boolean enabledAutoObserver = false;
    public String strategyOverride;
    public String openerOverride;

    public Config() {

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.enabledAutoObserver = Boolean.parseBoolean(dotenv.get("IA_ENABLE_AUTO_OBSERVER"));
        this.strategyOverride = dotenv.get("IA_STRATEGY_OVERRIDE");
        this.openerOverride = dotenv.get("IA_OPENER_OVERRIDE");
    }
}
