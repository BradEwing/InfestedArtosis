package macro;

import config.Config;
import macro.BunkerStance.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerEconSwitchTest {

    @Test
    void theEconomyAnswerIsOnByDefault() {
        assertTrue(Config.bunkerEcon);
    }

    @Test
    void withTheSwitchOffNoStanceStandsWhateverTheBunkerAndBuildSay() {
        BunkerStance stance = new BunkerStance();

        stance.setStatus(BunkerStance.evaluate(false, true, false, false, true), true);

        assertFalse(stance.isWanted());
        assertEquals(0, stance.getStanceId());
        assertEquals(Status.SWITCH_OFF, BunkerStance.evaluate(false, true, false, false, true));
    }

    @Test
    void withTheSwitchOffNoDroneRoundOpens() {
        BunkerStance stance = new BunkerStance();
        DroneRound round = new DroneRound();

        stance.setStatus(BunkerStance.evaluate(false, true, false, false, true), true);
        round.update(6000, 0, 14, 0, true, false, DroneRound.ContainHeld.builder()
                .workers(14)
                .softCap(30)
                .hardCap(40)
                .bunkerStanceId(stance.getStanceId())
                .build());

        assertFalse(round.isActive());
    }
}
