package strategy.buildorder.terran;

import bwapi.Race;
import info.BuildOrderChain;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;
import util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LurkerDefilerUltraTransitionTest {

    private static final Time EARLY = new Time(9, 0);

    private static final Time JUST_BEFORE_CLOCK = new Time(12, 59);

    private static final int FOUR_STARTING_LOCATIONS = 4;

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    @Test
    void twoHatchMutaWaitsWhileGoliathsAreFewAndTheClockIsEarly() {
        assertNull(LurkerDefilerUltraTransition.twoHatchMutaTrigger(5, JUST_BEFORE_CLOCK));
        assertNull(LurkerDefilerUltraTransition.twoHatchMutaTrigger(0, EARLY));
    }

    @Test
    void twoHatchMutaTriggersOnSixGoliaths() {
        assertEquals(LurkerDefilerUltraTransition.Trigger.GOLIATHS,
                LurkerDefilerUltraTransition.twoHatchMutaTrigger(6, EARLY));
    }

    @Test
    void twoHatchMutaTriggersOnTheClockAtThirteenMinutes() {
        assertEquals(LurkerDefilerUltraTransition.Trigger.CLOCK,
                LurkerDefilerUltraTransition.twoHatchMutaTrigger(0, new Time(13, 0)));
        assertEquals(LurkerDefilerUltraTransition.Trigger.CLOCK,
                LurkerDefilerUltraTransition.twoHatchMutaTrigger(0, new Time(20, 0)));
    }

    @Test
    void twoHatchMutaNamesGoliathsWhenBothTriggersHold() {
        assertEquals(LurkerDefilerUltraTransition.Trigger.GOLIATHS,
                LurkerDefilerUltraTransition.twoHatchMutaTrigger(6, new Time(13, 0)));
    }

    @Test
    void threeHatchLurkerTriggersOnTheFourthLurkerMorphed() {
        assertNull(LurkerDefilerUltraTransition.threeHatchLurkerTrigger(3));
        assertEquals(LurkerDefilerUltraTransition.Trigger.LURKERS,
                LurkerDefilerUltraTransition.threeHatchLurkerTrigger(4));
    }

    @Test
    void theEconomyGateNeedsTwentyOneDronesAndThreeBases() {
        assertFalse(LurkerDefilerUltraTransition.economyReady(20, 3));
        assertFalse(LurkerDefilerUltraTransition.economyReady(21, 2));
        assertTrue(LurkerDefilerUltraTransition.economyReady(21, 3));
    }

    @Test
    void aTriggerWithoutTheEconomyDoesNotTransition() {
        LurkerDefilerUltraTransition.Trigger goliaths = LurkerDefilerUltraTransition.Trigger.GOLIATHS;
        assertFalse(LurkerDefilerUltraTransition.shouldEnter(goliaths, 20, 3));
        assertFalse(LurkerDefilerUltraTransition.shouldEnter(goliaths, 40, 2));
        assertTrue(LurkerDefilerUltraTransition.shouldEnter(goliaths, 21, 3));
    }

    @Test
    void theEconomyWithoutATriggerDoesNotTransition() {
        assertFalse(LurkerDefilerUltraTransition.shouldEnter(null, 60, 5));
    }

    @Test
    void twoHatchMutaAndThreeHatchLurkerOfferOnlyLurkerDefilerUltra() {
        assertOffersOnlyLurkerDefilerUltra(new TwoHatchMuta().transition(null));
        assertOffersOnlyLurkerDefilerUltra(new ThreeHatchLurker().transition(null));
    }

    @Test
    void crazyZergIsTerminalAndKeepsTheDefaults() {
        assertThrows(NoSuchMethodException.class,
                () -> CrazyZerg.class.getDeclaredMethod("shouldTransition", info.GameState.class));
        assertTrue(new CrazyZerg().transition(null).isEmpty());
    }

    @Test
    void lurkerDefilerUltraIsTerminal() {
        assertThrows(NoSuchMethodException.class,
                () -> LurkerDefilerUltra.class.getDeclaredMethod("shouldTransition", info.GameState.class));
        assertTrue(new LurkerDefilerUltra().transition(null).isEmpty());
    }

    @Test
    void theLabelNamesBothBuildsAndTheTrigger() {
        assertEquals("2HatchMuta>LurkerDefilerUltra:GOLIATHS",
                LurkerDefilerUltraTransition.label("2HatchMuta", LurkerDefilerUltraTransition.Trigger.GOLIATHS));
        assertEquals("3HatchLurker>LurkerDefilerUltra:LURKERS",
                LurkerDefilerUltraTransition.label("3HatchLurker", LurkerDefilerUltraTransition.Trigger.LURKERS));
    }

    @Test
    void theTransitionLabelReachesTheSink() {
        List<String> labels = new ArrayList<>();
        PlanEvents.register(new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
            }

            @Override
            public void onBuildOrderTransition(String transitionLabel) {
                labels.add(transitionLabel);
            }
        });

        PlanEvents.buildOrderTransition("2HatchMuta>LurkerDefilerUltra:CLOCK");

        assertEquals(Collections.singletonList("2HatchMuta>LurkerDefilerUltra:CLOCK"), labels);
    }

    @Test
    void theFactoryRegistersItAsATerranBuildButNotAsAnOpener() {
        BuildOrderFactory factory = new BuildOrderFactory(FOUR_STARTING_LOCATIONS, Race.Terran);

        BuildOrder buildOrder = factory.getByName(LurkerDefilerUltra.NAME);
        assertNotNull(buildOrder);
        assertFalse(buildOrder.isOpener());
        assertFalse(factory.isPlayableOpener(buildOrder));
        assertFalse(factory.getOpenerNames().contains(LurkerDefilerUltra.NAME));
        assertTrue(factory.getPlayableNonOpenerNames().contains(LurkerDefilerUltra.NAME));
    }

    @Test
    void itIsNotPlayableAgainstTheOtherRaces() {
        for (Race race : new Race[]{Race.Protoss, Race.Zerg}) {
            BuildOrderFactory factory = new BuildOrderFactory(FOUR_STARTING_LOCATIONS, race);
            assertFalse(factory.getPlayableNonOpenerNames().contains(LurkerDefilerUltra.NAME), race.toString());
        }
    }

    @Test
    void theBuildOrderChainRecordsTheHandover() {
        BuildOrderChain chain = new BuildOrderChain();
        chain.add("2HatchMuta");
        for (BuildOrder candidate : new TwoHatchMuta().transition(null)) {
            chain.add(candidate.getName());
        }

        assertEquals("2HatchMuta;LurkerDefilerUltra", chain.join());
    }

    private static void assertOffersOnlyLurkerDefilerUltra(Set<BuildOrder> candidates) {
        assertEquals(1, candidates.size());
        assertEquals(LurkerDefilerUltra.NAME, candidates.iterator().next().getName());
    }
}
