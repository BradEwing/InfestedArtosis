package unit.squad;

import bwapi.Race;
import bwapi.UnitType;
import config.Config;
import info.tracking.EnemySpeedTracker.Evidence;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SpeedlingAllIn;
import strategy.buildorder.opener.FourPool;
import strategy.buildorder.opener.NinePoolSpeed;
import strategy.buildorder.opener.TwelveHatch;
import unit.squad.SpeedParity.Reading;
import unit.squad.SpeedParity.Term;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedParityTest {

    private static final int BASE_THRESHOLD = 4;

    private static Map<UnitType, Integer> lings(int count) {
        Map<UnitType, Integer> composition = new HashMap<>();
        composition.put(UnitType.Zerg_Zergling, count);
        return composition;
    }

    private static Reading read(Race race, Map<UnitType, Integer> composition, boolean exempt, int alive,
                                boolean metabolic, boolean adrenal, Evidence enemy) {
        return SpeedParity.read(race, composition, exempt, alive, metabolic, adrenal, enemy);
    }

    @Test
    void aSquadOfLingsIsLingHeavy() {
        assertTrue(SpeedParity.lingHeavy(lings(12)));
    }

    @Test
    void aSquadOfHydrasIsNot() {
        Map<UnitType, Integer> composition = new HashMap<>();
        composition.put(UnitType.Zerg_Hydralisk, 8);
        composition.put(UnitType.Zerg_Zergling, 2);

        assertFalse(SpeedParity.lingHeavy(composition));
    }

    @Test
    void lingsAtHalfTheSupplyCountAsHeavy() {
        Map<UnitType, Integer> composition = new HashMap<>();
        composition.put(UnitType.Zerg_Zergling, 4);
        composition.put(UnitType.Zerg_Hydralisk, 2);

        assertTrue(SpeedParity.lingHeavy(composition));
    }

    @Test
    void anEmptySquadIsNotLingHeavy() {
        assertFalse(SpeedParity.lingHeavy(new HashMap<>()));
    }

    @Test
    void aLingSquadWithNoUpgradeNeedsMoreSupplyThanOneWithAdrenalGlands() {
        int bare = SpeedParity.applyTerm(BASE_THRESHOLD,
                SpeedParity.upgradeTerm(true, false, false, false, false));
        int adrenal = SpeedParity.applyTerm(BASE_THRESHOLD,
                SpeedParity.upgradeTerm(true, false, false, true, true));

        assertTrue(bare > adrenal);
        assertEquals(BASE_THRESHOLD + SpeedParity.UNUPGRADED_LING_BONUS, bare);
        assertEquals(BASE_THRESHOLD - SpeedParity.ADRENAL_DISCOUNT, adrenal);
    }

    @Test
    void metabolicBoostAloneLiftsTheBonusWithoutADiscount() {
        assertEquals(0, SpeedParity.upgradeTerm(true, false, false, true, false));
    }

    @Test
    void anAllInOrRushBuildIsExempt() {
        assertEquals(0, SpeedParity.upgradeTerm(true, true, false, false, false));
        assertEquals(0, SpeedParity.upgradeTerm(true, true, false, true, true));
    }

    @Test
    void sixtyFourLingsLiftTheBonus() {
        assertEquals(0, SpeedParity.upgradeTerm(true, false, true, false, false));
    }

    @Test
    void aSquadThatIsNotLingHeavyKeepsItsThreshold() {
        assertEquals(0, SpeedParity.upgradeTerm(false, false, false, false, false));
    }

    @Test
    void theDiscountNeverTakesAThresholdBelowTheFloor() {
        assertEquals(SpeedParity.ADRENAL_FLOOR, SpeedParity.applyTerm(3, -SpeedParity.ADRENAL_DISCOUNT));
        assertEquals(1, SpeedParity.applyTerm(1, -SpeedParity.ADRENAL_DISCOUNT));
    }

    @Test
    void aZvZLingSquadWithoutSpeedHoldsAgainstAnEnemyWithIt() {
        Reading seen = read(Race.Zerg, lings(20), false, 20, false, false, Evidence.SEEN);
        Reading plausible = read(Race.Zerg, lings(20), false, 20, false, false, Evidence.PLAUSIBLE);

        assertTrue(seen.isHolds());
        assertEquals(Term.SPEED_PARITY, seen.getTerm());
        assertTrue(plausible.isHolds());
    }

    @Test
    void withSpeedParityTheSquadDoesNotHold() {
        Reading parity = read(Race.Zerg, lings(20), false, 20, true, false, Evidence.SEEN);

        assertFalse(parity.isHolds());
        assertEquals(Term.NONE, parity.getTerm());
    }

    @Test
    void anEnemyWithoutSpeedDoesNotHoldTheSquadForIt() {
        Reading reading = read(Race.Zerg, lings(20), false, 20, false, false, Evidence.NONE);

        assertFalse(reading.isHolds());
        assertEquals(Term.LING_UPGRADE, reading.getTerm());
    }

    @Test
    void onlyAZergOpponentHoldsTheSquad() {
        assertFalse(read(Race.Protoss, lings(20), false, 20, false, false, Evidence.SEEN).isHolds());
        assertFalse(read(Race.Terran, lings(20), false, 20, false, false, Evidence.SEEN).isHolds());
    }

    @Test
    void volumeLiftsTheHold() {
        Reading reading = read(Race.Zerg, lings(40), false, SpeedParity.VOLUME_ZERGLINGS, false, false,
                Evidence.SEEN);

        assertFalse(reading.isHolds());
        assertEquals(Term.NONE, reading.getTerm());
    }

    @Test
    void anAllInBuildMovesOutOnItsOwnTiming() {
        Reading reading = read(Race.Zerg, lings(20), true, 20, false, false, Evidence.SEEN);

        assertFalse(reading.isHolds());
        assertEquals(0, reading.getUpgradeTerm());
    }

    @Test
    void theHoldNamesTheSpeedStatesForTelemetry() {
        Reading reading = read(Race.Zerg, lings(20), false, 20, false, false, Evidence.PLAUSIBLE);

        assertEquals("NONE", reading.getOurSpeed());
        assertEquals(Evidence.PLAUSIBLE, reading.getEnemyEvidence());
        assertEquals("METABOLIC", SpeedParity.ourSpeed(true, false));
        assertEquals("ADRENAL", SpeedParity.ourSpeed(true, true));
    }

    @Test
    void closeThreatsLeaveTheDecisionToTheSimulation() {
        assertTrue(SpeedParity.holdsHome(true, true, false));
        assertFalse(SpeedParity.holdsHome(true, true, true));
    }

    @Test
    void withTheSwitchOffTheThresholdAndTheHoldAreMains() {
        int term = SpeedParity.upgradeTerm(true, false, false, false, false);

        assertTrue(term > 0);
        assertEquals(BASE_THRESHOLD, SpeedParity.gatedThreshold(false, BASE_THRESHOLD, term));
        assertEquals(BASE_THRESHOLD + term, SpeedParity.gatedThreshold(true, BASE_THRESHOLD, term));
        assertFalse(SpeedParity.holdsHome(false, true, false));
    }

    @Test
    void theSwitchIsOnByDefault() {
        assertTrue(Config.speedParityGate);
    }

    @Test
    void theAllInAndRushBuildsAreExemptAndOthersAreNot() {
        assertTrue(new SpeedlingAllIn().isAllInOrRush());
        assertTrue(new FourPool().isAllInOrRush());
        assertTrue(new NinePoolSpeed().isAllInOrRush());
        BuildOrder macro = new TwelveHatch();
        assertFalse(macro.isAllInOrRush());
    }
}
