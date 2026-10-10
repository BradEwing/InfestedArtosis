package strategy.buildorder;

import org.junit.jupiter.api.Test;
import strategy.buildorder.opener.FourPool;
import strategy.buildorder.opener.NinePoolGasHatchSpeed8D;
import strategy.buildorder.opener.NinePoolSpeed;
import strategy.buildorder.opener.Overpool;
import strategy.buildorder.opener.ThreeHatchBeforePool;
import strategy.buildorder.opener.TwelveHatch;
import strategy.buildorder.opener.TwelvePool;
import strategy.buildorder.terran.ThreeHatchLurker;
import strategy.buildorder.terran.TwoHatchMuta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerEconBuildTest {

    @Test
    void buildsThatAreAllInsByDesignKeepTheirEconomy() {
        assertFalse(new SpeedlingT().allowsBunkerEcon(null));
        assertFalse(new SpeedlingP().allowsBunkerEcon(null));
        assertFalse(new SpeedlingZ().allowsBunkerEcon(null));
        assertFalse(new FourPool().allowsBunkerEcon(null));
        assertFalse(new NinePoolSpeed().allowsBunkerEcon(null));
        assertFalse(new NinePoolGasHatchSpeed8D().allowsBunkerEcon(null));
    }

    @Test
    void everyOtherBuildTakesDronesBehindABunkerStance() {
        assertTrue(new TwelvePool().allowsBunkerEcon(null));
        assertTrue(new Overpool().allowsBunkerEcon(null));
        assertTrue(new TwelveHatch().allowsBunkerEcon(null));
        assertTrue(new ThreeHatchBeforePool().allowsBunkerEcon(null));
        assertTrue(new ThreeHatchLurker().allowsBunkerEcon(null));
        assertTrue(new TwoHatchMuta().allowsBunkerEcon(null));
    }

    @Test
    void anAllInStrategySelectedBeforeTheOpenerHandsOverTurnsTheEconomyAnswerOffThroughTheOpener() {
        assertFalse(BuildOrder.bunkerEconAllowed(new ThreeHatchBeforePool(), new SpeedlingT(), null));
    }

    @Test
    void anAllInOpenerTurnsTheEconomyAnswerOffWhateverStrategyIsSelected() {
        assertFalse(BuildOrder.bunkerEconAllowed(new FourPool(), new TwoHatchMuta(), null));
    }

    @Test
    void withNoAllInOnEitherSideTheEconomyAnswerRuns() {
        assertTrue(BuildOrder.bunkerEconAllowed(new ThreeHatchBeforePool(), new TwoHatchMuta(), null));
        assertTrue(BuildOrder.bunkerEconAllowed(new ThreeHatchBeforePool(), null, null));
    }
}
