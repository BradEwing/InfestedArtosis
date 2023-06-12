package config;

import lombok.Value;

/**
 * Feature flags to toggle experimental features.
 *
 * TODO: Override from env
 */
public final class FeatureFlags {

    public static boolean learnDefensiveSunk = false;
}
