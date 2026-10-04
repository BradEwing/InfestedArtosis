package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import telemetry.BunkerHoldRelease;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerRetreatMemoryTest {

    private static final Position BUNKER = new Position(1000, 1000);
    private static final Position OTHER_BUNKER = new Position(1400, 1000);

    private static Map<UnitType, Integer> squad(int zerglings, int hydralisks) {
        Map<UnitType, Integer> counts = new EnumMap<>(UnitType.class);
        if (zerglings > 0) counts.put(UnitType.Zerg_Zergling, zerglings);
        if (hydralisks > 0) counts.put(UnitType.Zerg_Hydralisk, hydralisks);
        return counts;
    }

    @Test
    void aBunkerTheSquadRetreatedFromStaysHeldBeyondTheSampleRadius() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        memory.releaseIfGrown(squad(12, 0));
        memory.retain(Collections.singletonList(BUNKER));

        assertTrue(memory.holds(BUNKER));
    }

    @Test
    void aBunkerTheSquadNeverRetreatedFromIsNotHeld() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        assertFalse(memory.holds(OTHER_BUNKER));
    }

    @Test
    void lossesDoNotReleaseTheMemory() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        memory.releaseIfGrown(squad(9, 0));

        assertTrue(memory.holds(BUNKER));
    }

    @Test
    void moreOfAnyTypeReleasesTheMemory() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        memory.releaseIfGrown(squad(13, 0));

        assertFalse(memory.holds(BUNKER));
    }

    @Test
    void aNewTypeReleasesTheMemoryEvenWhenOthersAreLost() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        memory.releaseIfGrown(squad(6, 2));

        assertFalse(memory.holds(BUNKER));
    }

    @Test
    void aBunkerThatIsNoLongerLivingIsForgotten() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Arrays.asList(BUNKER, OTHER_BUNKER), squad(12, 0), 0);

        memory.retain(Collections.singletonList(OTHER_BUNKER));

        assertFalse(memory.holds(BUNKER));
        assertTrue(memory.holds(OTHER_BUNKER));
    }

    @Test
    void aRetreatThatPricedNoBunkerLeavesTheMemoryAsItWas() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        memory.record(Collections.<Position>emptyList(), squad(12, 0), 0);

        assertTrue(memory.holds(BUNKER));
    }

    @Test
    void aFreshRetreatReplacesTheRememberedBunkersAndTheirComposition() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        memory.record(Collections.singletonList(OTHER_BUNKER), squad(20, 0), 0);
        memory.releaseIfGrown(squad(20, 0));

        assertFalse(memory.holds(BUNKER));
        assertTrue(memory.holds(OTHER_BUNKER));
    }

    @Test
    void aSecondRetreatFromTheSameBunkerKeepsTheLargerComposition() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        memory.record(Collections.singletonList(BUNKER), squad(8, 0), 0);
        memory.releaseIfGrown(squad(9, 0));

        assertTrue(memory.holds(BUNKER));
    }

    @Test
    void aRememberedBunkerBeyondTheRadiusHoldsWhateverTheTimeSinceItWasSeen() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);
        Position farSquad = new Position(BUNKER.getX() + 2000, BUNKER.getY());
        double radius = HorizonCombatSimulator.edgeOfFireRadius(BunkerPricing.reach(false, 0));

        assertTrue(HorizonCombatSimulator.heldBeyondRadius(memory, BUNKER, farSquad, radius));
    }

    @Test
    void aRememberedBunkerInsideTheRadiusIsPricedNotHeld() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);
        Position nearSquad = new Position(BUNKER.getX() + 100, BUNKER.getY());
        double radius = HorizonCombatSimulator.edgeOfFireRadius(BunkerPricing.reach(false, 0));

        assertFalse(HorizonCombatSimulator.heldBeyondRadius(memory, BUNKER, nearSquad, radius));
    }

    @Test
    void anUnrememberedBunkerBeyondTheRadiusIsNotHeld() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        Position farSquad = new Position(BUNKER.getX() + 2000, BUNKER.getY());
        double radius = HorizonCombatSimulator.edgeOfFireRadius(BunkerPricing.reach(false, 0));

        assertFalse(HorizonCombatSimulator.heldBeyondRadius(memory, BUNKER, farSquad, radius));
    }

    @Test
    void anEmptySquadNeverGrew() {
        assertFalse(BunkerRetreatMemory.grew(squad(12, 0), Collections.<UnitType, Integer>emptyMap()));
    }

    private static BunkerRetreatMemory remembering(Position bunker, Map<UnitType, Integer> composition) {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(bunker), composition, 0);
        return memory;
    }

    private static BunkerRetreatMemory merged(BunkerRetreatMemory.Source... sources) {
        BunkerRetreatMemory merged = new BunkerRetreatMemory();
        merged.absorb(Arrays.asList(sources));
        return merged;
    }

    @Test
    void aMergedSquadHoldsTheUnionOfTheSourcesBunkers() {
        BunkerRetreatMemory a = remembering(BUNKER, squad(12, 0));
        BunkerRetreatMemory b = remembering(OTHER_BUNKER, squad(8, 0));

        BunkerRetreatMemory merged = merged(new BunkerRetreatMemory.Source(a, squad(12, 0)),
                new BunkerRetreatMemory.Source(b, squad(8, 0)));

        assertTrue(merged.holds(BUNKER));
        assertTrue(merged.holds(OTHER_BUNKER));
    }

    @Test
    void aMergedSquadIsNotReleasedByTheSumOfTheSourcesRecordedComposition() {
        BunkerRetreatMemory a = remembering(BUNKER, squad(12, 0));
        BunkerRetreatMemory b = remembering(BUNKER, squad(8, 0));

        BunkerRetreatMemory merged = merged(new BunkerRetreatMemory.Source(a, squad(12, 0)),
                new BunkerRetreatMemory.Source(b, squad(8, 0)));
        merged.releaseIfGrown(squad(20, 0));

        assertTrue(merged.holds(BUNKER));
    }

    private static BunkerRetreatMemory mergedWith(BunkerRetreatMemory held, Map<UnitType, Integer> heldNow,
                                                  Map<UnitType, Integer> reinforcement) {
        return merged(new BunkerRetreatMemory.Source(held, heldNow),
                new BunkerRetreatMemory.Source(new BunkerRetreatMemory(), reinforcement));
    }

    @Test
    void reinforcementsWithinTheMergeGrowthShareDoNotReleaseTheHold() {
        BunkerRetreatMemory retreated = remembering(BUNKER, squad(12, 0));

        BunkerRetreatMemory merged = mergedWith(retreated, squad(12, 0), squad(6, 0));
        merged.releaseIfGrown(squad(18, 0));

        assertTrue(merged.holds(BUNKER));
        assertEquals(BunkerHoldRelease.NONE, merged.takeReleaseReason());
    }

    @Test
    void reinforcementsPastTheMergeGrowthShareReleaseTheHold() {
        BunkerRetreatMemory retreated = remembering(BUNKER, squad(12, 0));

        BunkerRetreatMemory merged = mergedWith(retreated, squad(12, 0), squad(7, 0));

        assertFalse(merged.holds(BUNKER));
        assertEquals(BunkerHoldRelease.MERGE_GROWTH, merged.takeReleaseReason());
    }

    @Test
    void reinforcementsAreCountedBySupplyNotHeadcount() {
        BunkerRetreatMemory retreated = remembering(BUNKER, squad(12, 0));

        BunkerRetreatMemory merged = mergedWith(retreated, squad(12, 0), squad(0, 4));

        assertFalse(merged.holds(BUNKER));
    }

    @Test
    void reinforcementsThatMergeInOverSeveralMergesAddUpToARelease() {
        BunkerRetreatMemory retreated = remembering(BUNKER, squad(12, 0));

        BunkerRetreatMemory first = mergedWith(retreated, squad(12, 0), squad(4, 0));
        assertTrue(first.holds(BUNKER));
        BunkerRetreatMemory second = mergedWith(first, squad(16, 0), squad(2, 0));
        assertTrue(second.holds(BUNKER));
        BunkerRetreatMemory third = mergedWith(second, squad(18, 0), squad(1, 0));

        assertFalse(third.holds(BUNKER));
    }

    @Test
    void aSplitAndMergeAgainDoesNotCountTheReinforcementsTwice() {
        BunkerRetreatMemory retreated = remembering(BUNKER, squad(12, 0));
        BunkerRetreatMemory reinforced = mergedWith(retreated, squad(12, 0), squad(5, 0));
        BunkerRetreatMemory child = merged(new BunkerRetreatMemory.Source(reinforced, squad(8, 0)));

        BunkerRetreatMemory rejoined = merged(new BunkerRetreatMemory.Source(reinforced, squad(9, 0)),
                new BunkerRetreatMemory.Source(child, squad(8, 0)));

        assertTrue(rejoined.holds(BUNKER));
    }

    @Test
    void theHoldEndsOnceTheCapPassesSinceTheRetreat() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 100);

        memory.releaseIfExpired(100 + BunkerRetreatMemory.HOLD_CAP_FRAMES - 1);
        assertTrue(memory.holds(BUNKER));
        memory.releaseIfExpired(100 + BunkerRetreatMemory.HOLD_CAP_FRAMES);

        assertFalse(memory.holds(BUNKER));
        assertEquals(BunkerHoldRelease.TIME_CAP, memory.takeReleaseReason());
    }

    @Test
    void aMergedHoldExpiresOnTheEarliestRetreat() {
        BunkerRetreatMemory old = new BunkerRetreatMemory();
        old.record(Collections.singletonList(BUNKER), squad(12, 0), 0);
        BunkerRetreatMemory recent = new BunkerRetreatMemory();
        recent.record(Collections.singletonList(BUNKER), squad(8, 0), 1000);

        BunkerRetreatMemory merged = merged(new BunkerRetreatMemory.Source(old, squad(12, 0)),
                new BunkerRetreatMemory.Source(recent, squad(8, 0)));
        merged.releaseIfExpired(BunkerRetreatMemory.HOLD_CAP_FRAMES);

        assertFalse(merged.holds(BUNKER));
    }

    @Test
    void aFreshRetreatRestartsTheCap() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 0);

        memory.record(Collections.singletonList(BUNKER), squad(12, 0), 1000);
        memory.releaseIfExpired(BunkerRetreatMemory.HOLD_CAP_FRAMES);

        assertTrue(memory.holds(BUNKER));
    }

    @Test
    void eachReleaseNamesItsReasonOnce() {
        BunkerRetreatMemory grown = remembering(BUNKER, squad(12, 0));
        grown.releaseIfGrown(squad(13, 0));
        assertEquals(BunkerHoldRelease.GROWTH, grown.takeReleaseReason());
        assertEquals(BunkerHoldRelease.NONE, grown.takeReleaseReason());

        BunkerRetreatMemory dead = remembering(BUNKER, squad(12, 0));
        dead.retain(Collections.<Position>emptyList());
        assertEquals(BunkerHoldRelease.BUNKER_DIED, dead.takeReleaseReason());
    }

    @Test
    void aMemoryThatHoldsNothingReleasesNothing() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();

        memory.releaseIfGrown(squad(13, 0));
        memory.releaseIfExpired(100000);
        memory.retain(Collections.<Position>emptyList());

        assertEquals(BunkerHoldRelease.NONE, memory.takeReleaseReason());
    }

    @Test
    void supplyIsTheSumOfTheUnitsSupply() {
        assertEquals(UnitType.Zerg_Zergling.supplyRequired() * 6 + UnitType.Zerg_Hydralisk.supplyRequired() * 2,
                BunkerRetreatMemory.supply(squad(6, 2)));
    }

    @Test
    void aMergedSquadStillReleasesWhenItGrowsAfterTheMerge() {
        BunkerRetreatMemory retreated = remembering(BUNKER, squad(12, 0));

        BunkerRetreatMemory merged = merged(new BunkerRetreatMemory.Source(retreated, squad(12, 0)),
                new BunkerRetreatMemory.Source(new BunkerRetreatMemory(), squad(6, 0)));
        merged.releaseIfGrown(squad(19, 0));

        assertFalse(merged.holds(BUNKER));
    }

    @Test
    void aSourceThatGrewSinceItsRetreatContributesNoMemory() {
        BunkerRetreatMemory retreated = remembering(BUNKER, squad(12, 0));

        BunkerRetreatMemory merged = merged(new BunkerRetreatMemory.Source(retreated, squad(14, 0)),
                new BunkerRetreatMemory.Source(new BunkerRetreatMemory(), squad(6, 0)));

        assertFalse(merged.holds(BUNKER));
    }

    @Test
    void mergingSquadsThatRememberNothingRemembersNothing() {
        BunkerRetreatMemory merged = merged(
                new BunkerRetreatMemory.Source(new BunkerRetreatMemory(), squad(12, 0)),
                new BunkerRetreatMemory.Source(new BunkerRetreatMemory(), squad(6, 0)));
        merged.record(Collections.singletonList(BUNKER), squad(18, 0), 0);
        merged.releaseIfGrown(squad(18, 0));

        assertTrue(merged.holds(BUNKER));
        assertFalse(merged.holds(OTHER_BUNKER));
    }

    @Test
    void aSplitKeepsTheMemoryOnBothHalves() {
        BunkerRetreatMemory parent = remembering(BUNKER, squad(12, 0));
        BunkerRetreatMemory child = merged(new BunkerRetreatMemory.Source(parent, squad(12, 0)));

        child.releaseIfGrown(squad(4, 0));
        parent.releaseIfGrown(squad(8, 0));

        assertTrue(child.holds(BUNKER));
        assertTrue(parent.holds(BUNKER));
    }

    @Test
    void aRememberedBunkerInsideTheRadiusThatWentUnpricedHoldsTheAdvance() {
        Position priced = new Position(1, 1);
        Position unpriced = new Position(2, 2);

        assertTrue(HorizonCombatSimulator.anyUnpriced(Arrays.asList(priced, unpriced),
                Collections.singletonList(priced)));
        assertFalse(HorizonCombatSimulator.anyUnpriced(Collections.singletonList(priced),
                Collections.singletonList(priced)));
        assertFalse(HorizonCombatSimulator.anyUnpriced(Collections.<Position>emptyList(),
                Collections.<Position>emptyList()));
    }

    @Test
    void theHalvesOfASplitThatMergeAgainCountTheRetreatOnce() {
        BunkerRetreatMemory parent = remembering(BUNKER, squad(12, 0));
        BunkerRetreatMemory child = merged(new BunkerRetreatMemory.Source(parent, squad(12, 0)));

        BunkerRetreatMemory rejoined = merged(new BunkerRetreatMemory.Source(parent, squad(8, 0)),
                new BunkerRetreatMemory.Source(child, squad(4, 0)));

        rejoined.releaseIfGrown(squad(12, 0));
        assertTrue(rejoined.holds(BUNKER));
        rejoined.releaseIfGrown(squad(13, 0));
        assertFalse(rejoined.holds(BUNKER));
    }

    @Test
    void halvesThatSplitAndMergeTwiceStillReleaseAtOneUnitMore() {
        BunkerRetreatMemory first = remembering(BUNKER, squad(12, 0));
        BunkerRetreatMemory second = merged(new BunkerRetreatMemory.Source(first, squad(12, 0)));
        BunkerRetreatMemory rejoined = merged(new BunkerRetreatMemory.Source(first, squad(8, 0)),
                new BunkerRetreatMemory.Source(second, squad(4, 0)));
        BunkerRetreatMemory third = merged(new BunkerRetreatMemory.Source(rejoined, squad(12, 0)));
        BunkerRetreatMemory again = merged(new BunkerRetreatMemory.Source(rejoined, squad(6, 0)),
                new BunkerRetreatMemory.Source(third, squad(6, 0)));

        again.releaseIfGrown(squad(13, 0));

        assertFalse(again.holds(BUNKER));
    }

    @Test
    void squadsThatRetreatedSeparatelyStillSum() {
        BunkerRetreatMemory a = remembering(BUNKER, squad(12, 0));
        BunkerRetreatMemory b = remembering(BUNKER, squad(8, 0));
        BunkerRetreatMemory merged = merged(new BunkerRetreatMemory.Source(a, squad(12, 0)),
                new BunkerRetreatMemory.Source(b, squad(8, 0)));

        merged.releaseIfGrown(squad(21, 0));

        assertFalse(merged.holds(BUNKER));
    }
}
