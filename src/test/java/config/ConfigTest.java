package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void aSwitchThatIsOnUnlessFalseDefaultsOn() {
        assertTrue(Config.enabledUnlessFalse(null));
        assertTrue(Config.enabledUnlessFalse(""));
        assertTrue(Config.enabledUnlessFalse("true"));
    }

    @Test
    void aSwitchThatIsOnUnlessFalseTurnsOffOnTheWordFalseInAnyCase() {
        assertFalse(Config.enabledUnlessFalse("false"));
        assertFalse(Config.enabledUnlessFalse("FALSE"));
        assertFalse(Config.enabledUnlessFalse(" False "));
    }
}
