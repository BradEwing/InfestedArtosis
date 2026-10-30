package unit.squad;

import bwapi.Race;
import bwapi.UnitType;
import config.Config;
import info.tracking.EnemySpeedTracker.Evidence;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SpeedlingAllIn;
import strategy.buildorder.opener.FourPool;
import strategy.buildorder.opener.NinePoolGasHatchSpeed8D;
import strategy.buildorder.opener.NinePoolSpeed;
import strategy.buildorder.opener.TwelveHatch;
import unit.squad.SpeedParity.Reading;
import unit.squad.SpeedParity.Term;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedParityTest {

    private static final int BASE_THRESHOLD = 4;
    private static final int NOW = 4000;
    private static final BiPredicate<Squad, Squad> ALWAYS_NEAR = (first, second) -> true;

    private static class SizedSquad extends GroundSquad {
        private final int supply;

        SizedSquad(int supply) {
            this.supply = supply;
        }

        @Override
        public int getSupply() {
            return supply;
        }
    }

    private static Squad squad(SquadStatus status, int supply) {
        Squad squad = new SizedSquad(supply);
        squad.setStatus(status);
        return squad;
    }

    private static boolean engaged(Squad squad, List<Squad> squads, Set<Squad> turned, int now) {
        return SquadManager.engagedForSpeedParity(squad, squads, ALWAYS_NEAR, turned, now);
    }

    private static SquadManager.SharedOutcome share(List<Squad> squads, Set<Squad> turned, int now) {
        turned.clear();
        return SquadManager.shareVerdicts(SharedVerdict.Exclusion.NONE, squads, new HashSet<>(squads),
                Collections.emptySet(), ALWAYS_NEAR, now, turned::add, squad -> { });
    }

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
    void aFightingSquadUnderAFightLockIsNotHeld() {
        assertTrue(SpeedParity.exemptWhileEngaged(SquadStatus.FIGHT, true));
    }

    @Test
    void theSameSquadWithoutTheLockIsHeld() {
        assertFalse(SpeedParity.exemptWhileEngaged(SquadStatus.FIGHT, false));
        assertFalse(SpeedParity.exemptWhileEngaged(SquadStatus.RALLY, true));
        assertFalse(SpeedParity.exemptWhileEngaged(SquadStatus.CONTAIN, true));
    }

    @Test
    void theSwitchesAreOnByDefault() {
        assertTrue(Config.speedParityGate);
        assertTrue(Config.lingUpgradeBar);
    }

    @Test
    void theHoldReleasesAContainEvenWithEnemiesNearButLeavesAnyOtherSquadWithThemToTheSim() {
        assertTrue(SpeedParity.enforced(true, SquadStatus.CONTAIN, true, false));
        assertFalse(SpeedParity.enforced(true, SquadStatus.RALLY, true, false));
        assertTrue(SpeedParity.enforced(true, SquadStatus.RALLY, false, false));
        assertFalse(SpeedParity.enforced(true, SquadStatus.FIGHT, false, true));
        assertFalse(SpeedParity.enforced(false, SquadStatus.CONTAIN, true, false));
    }

    @Test
    void aFightingSquadUnderItsOwnFightLockOrTurnBackCommitIsExempt() {
        Squad locked = squad(SquadStatus.FIGHT, 12);
        locked.startFightLock(NOW);
        Squad committed = squad(SquadStatus.FIGHT, 12);
        committed.startTurnBackCommit(NOW);
        Squad loose = squad(SquadStatus.FIGHT, 12);

        assertTrue(engaged(locked, Collections.singletonList(locked), Collections.emptySet(), NOW));
        assertTrue(engaged(committed, Collections.singletonList(committed), Collections.emptySet(), NOW));
        assertFalse(engaged(loose, Collections.singletonList(loose), Collections.emptySet(), NOW));
    }

    @Test
    void aFightingSquadHeldByANeighbourFightLockIsExemptOnlyWithTheSharedVerdictOn() {
        Squad neighbour = squad(SquadStatus.FIGHT, 12);
        neighbour.startFightLock(NOW);
        Squad held = squad(SquadStatus.FIGHT, 6);
        List<Squad> squads = Arrays.asList(neighbour, held);
        boolean before = Config.squadSharedVerdict;
        try {
            Config.squadSharedVerdict = true;
            assertTrue(engaged(held, squads, Collections.emptySet(), NOW));
            Config.squadSharedVerdict = false;
            assertFalse(engaged(held, squads, Collections.emptySet(), NOW));
        } finally {
            Config.squadSharedVerdict = before;
        }
    }

    @Test
    void aSquadTurnedToFightByTheSharedVerdictIsExemptOnlyWithTheSharedVerdictOn() {
        Squad turned = squad(SquadStatus.FIGHT, 6);
        Set<Squad> turns = new HashSet<>(Collections.singletonList(turned));
        boolean before = Config.squadSharedVerdict;
        try {
            Config.squadSharedVerdict = true;
            assertTrue(engaged(turned, Collections.singletonList(turned), turns, NOW));
            Config.squadSharedVerdict = false;
            assertFalse(engaged(turned, Collections.singletonList(turned), turns, NOW));
        } finally {
            Config.squadSharedVerdict = before;
        }
    }

    @Test
    void aSharedFightTurnIsNotUndoneByTheHoldOnTheNextFrameEvenWhenTheAdoptedLockHasRunOut() {
        Squad fighter = squad(SquadStatus.FIGHT, 12);
        fighter.adoptFightLock(NOW, NOW + 1);
        Squad retreater = squad(SquadStatus.RETREAT, 4);
        List<Squad> squads = Arrays.asList(fighter, retreater);
        Set<Squad> turned = new HashSet<>();

        SquadManager.SharedOutcome first = share(squads, turned, NOW);

        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
        assertTrue(turned.contains(retreater));
        assertFalse(retreater.isFightLocked(NOW + 1));
        boolean exempt = engaged(retreater, squads, turned, NOW + 1);
        assertTrue(exempt);
        assertFalse(SpeedParity.enforced(true, retreater.getStatus(), false, exempt));
        assertEquals(1, first.turns.size());

        SquadManager.SharedOutcome second = share(squads, turned, NOW + 1);

        assertTrue(second.turns.isEmpty());
        assertTrue(second.opposed.isEmpty());
        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
    }

    @Test
    void aHeldSquadIsNoSharedVerdictPartnerAndStaysHeldOnTheNextFrame() {
        Squad fighter = squad(SquadStatus.FIGHT, 6);
        fighter.startFightLock(NOW);
        Squad held = squad(SquadStatus.RALLY, 20);
        List<Squad> squads = Arrays.asList(fighter, held);
        Set<Squad> turned = new HashSet<>();

        for (int frame = NOW; frame < NOW + 2; frame++) {
            boolean exempt = engaged(held, squads, turned, frame);
            assertTrue(SpeedParity.enforced(true, held.getStatus(), false, exempt));
            SquadManager.SharedOutcome outcome = share(squads, turned, frame);
            assertTrue(outcome.turns.isEmpty());
            assertTrue(outcome.opposed.isEmpty());
            assertEquals(SquadStatus.RALLY, held.getStatus());
            assertEquals(SquadStatus.FIGHT, fighter.getStatus());
        }
    }

    @Test
    void withTheLingUpgradeBarOffTheThresholdIsMains() {
        int term = SpeedParity.upgradeTerm(true, false, false, false, true);

        assertEquals(BASE_THRESHOLD, SpeedParity.gatedThreshold(false, BASE_THRESHOLD, term));
        assertEquals(BASE_THRESHOLD - SpeedParity.ADRENAL_DISCOUNT,
                SpeedParity.gatedThreshold(true, BASE_THRESHOLD, term));
    }

    @Test
    void theAllInAndRushBuildsAreExemptAndOthersAreNot() {
        assertTrue(new SpeedlingAllIn().isAllInOrRush());
        assertTrue(new FourPool().isAllInOrRush());
        assertTrue(new NinePoolSpeed().isAllInOrRush());
        assertTrue(new NinePoolGasHatchSpeed8D().isAllInOrRush());
        BuildOrder macro = new TwelveHatch();
        assertFalse(macro.isAllInOrRush());
    }
}
