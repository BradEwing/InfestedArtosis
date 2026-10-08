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
        assertFalse(new SpeedlingAllIn().allowsBunkerEcon(null));
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
}
