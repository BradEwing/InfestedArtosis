package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTest {

    @Test
    void theGuardianBranchIsLeftToLearningWhenUnset() {
        assertEquals(GuardianBranchMode.LEARNED, GuardianBranchMode.parse(null));
        assertEquals(GuardianBranchMode.LEARNED, GuardianBranchMode.parse(""));
        assertEquals(GuardianBranchMode.LEARNED, GuardianBranchMode.parse("auto"));
    }

    @Test
    void theGuardianBranchIsForcedOnByTheWordTrueInAnyCase() {
        assertEquals(GuardianBranchMode.ON, GuardianBranchMode.parse("true"));
        assertEquals(GuardianBranchMode.ON, GuardianBranchMode.parse(" TRUE "));
    }

    @Test
    void theGuardianBranchIsForcedOffByTheWordFalseInAnyCase() {
        assertEquals(GuardianBranchMode.OFF, GuardianBranchMode.parse("false"));
        assertEquals(GuardianBranchMode.OFF, GuardianBranchMode.parse("FALSE"));
        assertEquals(GuardianBranchMode.OFF, GuardianBranchMode.parse(" False "));
    }
}
