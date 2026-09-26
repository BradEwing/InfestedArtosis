package strategy.buildorder;

import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The structural guarantees that make the larva-bound macro hatchery run by default.
 *
 * <p>A build order cannot reach the step by itself: it is reached through
 * {@link BuildOrder#plan}, which needs a live GameState. What is asserted here is the shape that
 * makes the step unavoidable - plan is final, the tech condition is abstract, and no build order
 * inherits an answer to it - plus the pure rules the step reads.
 */
class MacroHatcheryTemplateTest {

    private static final int TWO_STARTING_LOCATIONS = 2;

    private static final int FRAME = 14190;

    private static final TilePosition MAIN_TILE = new TilePosition(103, 105);

    private static final boolean OPENER = true;

    private static final boolean TERMINAL_BUILD = false;

    private static final String OPENER_PACKAGE = ".opener";

    private static final int REGISTERED_OPENERS = 7;

    /**
     * A build order that says nothing at all about the macro hatchery: it answers the abstract
     * hooks and no more. It still gets the shared step, because plan is final and runs it.
     */
    private static final class SilentBuildOrder extends BuildOrder {
        private SilentBuildOrder() {
            super("Silent");
        }

        @Override
        protected List<Plan> buildPlans(GameState gameState) {
            return Collections.emptyList();
        }

        @Override
        protected boolean macroHatcheryTechReady(TechProgression techProgression) {
            return true;
        }

        @Override
        public boolean playsRace(Race race) {
            return true;
        }
    }

    private static List<BuildOrder> registeredBuildOrders() {
        BuildOrderFactory factory = new BuildOrderFactory(TWO_STARTING_LOCATIONS, Race.Terran);
        List<BuildOrder> buildOrders = new ArrayList<>();
        for (String name : factory.getAllBuildOrderNames()) {
            buildOrders.add(factory.getByName(name));
        }
        return buildOrders;
    }

    @Test
    void theSharedStepCannotBeOverriddenAway() throws NoSuchMethodException {
        Method plan = BuildOrder.class.getDeclaredMethod("plan", GameState.class);

        assertTrue(Modifier.isFinal(plan.getModifiers()));
    }

    @Test
    void aBuildOrderThatNeverMentionsTheHookStillRunsTheSharedStep() throws NoSuchMethodException {
        Method plan = SilentBuildOrder.class.getMethod("plan", GameState.class);

        assertEquals(BuildOrder.class, plan.getDeclaringClass());
        assertTrue(BuildOrder.runsLarvaBoundMacroHatchery(TERMINAL_BUILD, new SilentBuildOrder().buildPlans(null)));
    }

    @Test
    void theTechConditionIsAbstractSoANewBuildMustStateIt() throws NoSuchMethodException {
        Method hook = BuildOrder.class.getDeclaredMethod("macroHatcheryTechReady", TechProgression.class);

        assertTrue(Modifier.isAbstract(hook.getModifiers()));
    }

    @Test
    void everyRegisteredBuildOrderStatesItsOwnTechCondition() {
        for (BuildOrder buildOrder : registeredBuildOrders()) {
            try {
                buildOrder.getClass().getDeclaredMethod("macroHatcheryTechReady", TechProgression.class);
            } catch (NoSuchMethodException e) {
                fail(buildOrder.getName() + " inherits its macro hatchery tech condition");
            }
        }
    }

    @Test
    void noMatchupBaseClassSuppliesALineageDefaultForTheTechCondition() {
        for (BuildOrder buildOrder : registeredBuildOrders()) {
            Class<?> parent = buildOrder.getClass().getSuperclass();
            while (parent != null && parent != BuildOrder.class) {
                assertFalse(declaresTechCondition(parent), parent.getSimpleName() + " defaults the tech condition");
                parent = parent.getSuperclass();
            }
        }
    }

    private static boolean declaresTechCondition(Class<?> type) {
        try {
            type.getDeclaredMethod("macroHatcheryTechReady", TechProgression.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Test
    void anOpenerNeverReachesTheSharedStep() {
        assertFalse(BuildOrder.runsLarvaBoundMacroHatchery(OPENER, Collections.emptyList()));
    }

    /**
     * Every build order in the opener package reports itself an opener, which is what holds it out
     * of the shared step. An opener that forgot the override would reach the step and write gate
     * rows under its own name.
     */
    @Test
    void everyBuildOrderInTheOpenerPackageIsHeldOutOfTheSharedStep() {
        int openers = 0;
        for (BuildOrder buildOrder : registeredBuildOrders()) {
            if (!buildOrder.getClass().getPackage().getName().endsWith(OPENER_PACKAGE)) {
                continue;
            }
            openers++;
            assertTrue(buildOrder.isOpener(), buildOrder.getName() + " does not report itself an opener");
            assertFalse(BuildOrder.runsLarvaBoundMacroHatchery(buildOrder.isOpener(), Collections.emptyList()),
                    buildOrder.getName() + " would write a gate row");
        }

        assertEquals(REGISTERED_OPENERS, openers);
    }

    @Test
    void noBuildOrderOutsideTheOpenerPackageIsHeldOut() {
        for (BuildOrder buildOrder : registeredBuildOrders()) {
            if (buildOrder.getClass().getPackage().getName().endsWith(OPENER_PACKAGE)) {
                continue;
            }
            assertTrue(BuildOrder.runsLarvaBoundMacroHatchery(buildOrder.isOpener(), Collections.emptyList()),
                    buildOrder.getName() + " would never consult the gate");
        }
    }

    @Test
    void aTerminalBuildThatPlannedNothingReachesTheSharedStep() {
        assertTrue(BuildOrder.runsLarvaBoundMacroHatchery(TERMINAL_BUILD, Collections.emptyList()));
    }

    @Test
    void aBuildThatSaysNothingAboutACapLetsTheSharedStepRun() {
        assertTrue(new SilentBuildOrder().allowsLarvaBoundMacroHatchery(null));
    }

    @Test
    void theSharedStepStandsDownBehindABuildsOwnHatchery() {
        List<Plan> plans = new ArrayList<>();
        plans.add(BuildOrder.macroHatcheryPlan(FRAME, MAIN_TILE));

        assertTrue(BuildOrder.containsHatcheryPlan(plans));
        assertFalse(BuildOrder.runsLarvaBoundMacroHatchery(TERMINAL_BUILD, plans));
    }

    @Test
    void theSharedStepStandsDownBehindAnExpansion() {
        List<Plan> plans = new ArrayList<>();
        plans.add(new BuildingPlan(UnitType.Zerg_Hatchery, FRAME, MAIN_TILE));

        assertTrue(BuildOrder.containsHatcheryPlan(plans));
    }

    @Test
    void anotherBuildingIsNotAHatchery() {
        List<Plan> plans = new ArrayList<>();
        plans.add(new BuildingPlan(UnitType.Zerg_Creep_Colony, FRAME, MAIN_TILE));
        plans.add(new BuildingPlan(UnitType.Zerg_Extractor, FRAME, MAIN_TILE));

        assertFalse(BuildOrder.containsHatcheryPlan(plans));
        assertTrue(BuildOrder.runsLarvaBoundMacroHatchery(TERMINAL_BUILD, plans));
    }

    @Test
    void aUnitPlanIsNotAHatchery() {
        List<Plan> plans = new ArrayList<>();
        plans.add(new UnitPlan(UnitType.Zerg_Drone, FRAME));

        assertFalse(BuildOrder.containsHatcheryPlan(plans));
    }
}
