package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigTest {

    @Test
    public void lurkerFireAwareFieldDefaultsOn() {
        assertTrue(Config.lurkerFireAware);
    }

    @Test
    public void unsetSettingEnablesSwitch() {
        assertTrue(Config.enabledUnlessFalse(null));
    }

    @Test
    public void explicitTrueEnablesSwitch() {
        assertTrue(Config.enabledUnlessFalse("true"));
    }

    @Test
    public void explicitFalseDisablesSwitch() {
        assertFalse(Config.enabledUnlessFalse("false"));
        assertFalse(Config.enabledUnlessFalse("FALSE"));
    }
}
